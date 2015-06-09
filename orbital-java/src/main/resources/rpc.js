var util = require('util');
var os = require('os');
var fs = require('fs');
var child_process = require('child_process');

var SYNC_BYTE = new Buffer([0xff]);

var RPC = function() {
	this.__packet = new Buffer(0);
	this.__seqId = 0;
	this.__endpoints = {};
	this.__callbacks = {};
	this.__readBuffer = new Buffer(1024 * 1024);
}

RPC.prototype.getName = function() {
	return this.__name;
}

RPC.prototype.start = function(pipe) {
	console.log(pipe);

	if (os.platform() == "windows") {
		// net.server
	} else {
		if (pipe === undefined) {
			pipe = os.tmpdir() + "/ipc" + require('crypto').randomBytes(32).toString('hex');
		}

		this.__name = pipe;

		// On posix environments, the PIPE env variable specifies a folder 
		// where we create two FIFOs: i and o.

		// Create the FIFOs in a tmp folder
		fs.mkdirSync(pipe);
		fs.mkdirSync(pipe + "/tmp");

		child_process.spawnSync('mkfifo', [pipe + '/tmp/i']);
		child_process.spawnSync('mkfifo', [pipe + '/tmp/o']);

		fs.renameSync(pipe + "/tmp", pipe + "/fifo");

		// We write to the 'i' pipe
		fs.open(pipe + "/fifo/i", "w", function(err, fd) {
			this.__writeFd = fd;
			this.__writer = fs.createWriteStream(null, { fd: fd });
			this.__writer.on('end', function() {
				this.__die("Write pipe closed");
			});
		}.bind(this));

		// We read from the 'o' pipe
		fs.open(pipe + "/fifo/o", "r", function(err, fd) {
			this.__readFd = fd;
			fs.read(fd, this.__readBuffer, 0, this.__readBuffer.length, null, this.__onRead.bind(this));
		}.bind(this));
	}

	// if (process.stdin.isTTY)
	// 	process.stdin.setRawMode(true);
		
	// process.stdin.on('data', this.__onPacketDataReceived.bind(this));
}

RPC.prototype.registerEndpoint = function(endpoint, fn) {
	this.__endpoints[endpoint] = fn;
}

RPC.prototype.call = function(endpoint, data, cb) {
	var seqId = ++this.__seqId;
	this.__callbacks[seqId] = cb;

	var packet = { call: true, seqId: seqId, endpoint: endpoint, data: data };
	this.__writePacket(packet);
}

RPC.prototype.callNoReturn = function(endpoint, data) {
	var packet = { call: true, seqId: 0, endpoint: endpoint, data: data };
	this.__writePacket(packet);
}

RPC.prototype.__processPacket = function(packet) {
	if (packet.endpoint) {
		if (this.__endpoints[packet.endpoint]) {
			var returnValue = this.__endpoints[packet.endpoint](packet.data);
			// TODO: try/catch
			if (packet.seqId) {
				// Send it back!
				this.__writePacket({ isCall: false, seqId: packet.seqId, data: returnValue });
			}
		} else {
			console.log("Endpoint not found: " + packet.endpoint);
		}
	} else {
		var cb = this.__callbacks[packet.seqId];
		if (cb) {
			delete this.__callbacks[packet.seqId];
			cb(packet.data);
		} else {
			console.log("Callback not found: " + packet.seqId);
		}
	}
}

RPC.prototype.__encodePacket = function(packet) {
	var binary = packet.data ? packet.data.binary : null;
	var json = (packet.data && packet.data.json) ? new Buffer(JSON.stringify(packet.data.json), 'utf8') : null;
	var endpoint = packet.endpoint ? new Buffer(packet.endpoint, 'utf8') : null;

	var capacity = 1;
	capacity += 4; // seqId
	if (endpoint)
		capacity += 4 + endpoint.length;
	if (binary)
		capacity += 4 + binary.length;
	if (json)
		capacity += 4 + json.length;

	var buf = new Buffer(capacity);

	var flags = (packet.isCall ? 1 : 0) 
			| (endpoint ? 1 << 1 : 0) 
			| (binary ? 1 << 2 : 0) 
			| (json ? 1 << 3 : 0);
	buf[0] = flags;
	var offset = 1;
	buf.writeIntBE(packet.seqId, offset, 4);
	offset += 4;

	if (endpoint) {
		buf.writeIntBE(endpoint.length, offset, 4);
		offset += 4;
		endpoint.copy(buf, offset);
		offset += endpoint.length;
	}
	if (binary) {
		buf.writeIntBE(binary.length, offset, 4);
		offset += 4;
		binary.copy(buf, offset);
		offset += binary.length;
	}
	if (json) {
		buf.writeIntBE(json.length, offset, 4);
		offset += 4;
		json.copy(buf, offset);
		offset += json.length;
	}

	return buf;
}

RPC.prototype.__decodePacket = function(bytes) {
	var out = { data: {} };
	
	var flags = bytes[0];
	var offset = 1;
	out.seqId = bytes.readIntBE(offset, 4);
	offset += 4;
	
	out.isCall = (flags & 1);
	var hasEndpoint = (flags & (1 << 1));
	var hasBinary = (flags & (1 << 2));
	var hasJson = (flags & (1 << 3));
	
	if (hasEndpoint) {
		var len = bytes.readIntBE(offset, 4);
		offset += 4;
		out.endpoint = bytes.toString('utf8', offset, offset + len);
		offset += len;
	}
	
	if (hasBinary) {
		var len = bytes.readIntBE(offset, 4);
		offset += 4;
		out.data.binary = bytes.slice(offset, offset + len);
		offset += len;
	}
	
	if (hasJson) {
		var len = bytes.readIntBE(offset, 4);
		offset += 4;
		out.data.json = JSON.parse(bytes.toString('utf8', offset, offset + len));
		offset += len;
	}
	
	return out;
}

RPC.prototype.__checkPacket = function() {
	for (var i = 0; i < 10; i++) {
		if (this.__packet[i] == 0xa) {
			// OK, we have a length. Now do we have enough bytes to fill that packet?
			var len = this.__packet.slice(1, i).toString();
			var length = parseInt(len, 16);
			if (this.__packet.length > i + length) {
				// yep, keep the rest around for another packet
				var packet = this.__decodePacket(this.__packet.slice(i + 1, i + length + 1));
				this.__packet = this.__packet.slice(i + length + 1);
				this.__processPacket(packet);
				return true;
			}
		}
	}

	return false;
}

RPC.prototype.__onPacketDataReceived = function(chunk) {
	this.__packet = Buffer.concat([this.__packet, chunk]);

	while (this.__checkPacket()) {}	
}

RPC.prototype.__writePacket = function(packet) {
	this.__writer.write(SYNC_BYTE, this.__checkWrite.bind(this));

	var buf = this.__encodePacket(packet);
	this.__writer.write(buf.length.toString(16) + "\n", this.__checkWrite.bind(this));
	this.__writer.write(buf, this.__checkWrite.bind(this));
}

RPC.prototype.__checkWrite = function(err) {
	// If the pipe closes, abort the entire app
	if (err) {
		this.__die("Error writing pipe");
	}
}

RPC.prototype.__onRead = function(err, bytesRead, buffer) {
	// If the pipe closes, abort the entire app
	if (err || bytesRead == 0) {
		this.__die("Error reading pipe");
	}

	this.__onPacketDataReceived(buffer.slice(0, bytesRead));
	fs.read(this.__readFd, this.__readBuffer, 0, this.__readBuffer.length, null, this.__onRead.bind(this));
};

RPC.prototype.__die = function(reason) {
	console.log("DIE: " + reason);
	process.exit(1);
};

var rpc = new RPC();

module.exports = rpc;
