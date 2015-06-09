var module = require('module');
console.log(module);

var rpc = require('rpc');
var path = require('path');
var util = require('util');
var app = require('app');
var process = require('process');
var child_process = require('child_process');

process.stdin.on('end', function() {
	// Immediate exit on host death
	process.exit(1);
});

var rootPaths = {
	app: process.env['APP_PATH'],
	web: process.env['WEB_PATH']
};

var pipe = process.env['PIPE'];

if (pipe)
	rpc.start(pipe);
else
	rpc.start();

app.on('ready', function() {
	console.log('ready!');
	var protocol = require('protocol');
	protocol.registerProtocol('app', function(request) {
		var url = request.url.slice(4);
		if (url.slice(-1) == '/')
			url += "index.html";
		if (url.indexOf('#') != -1)
			url = url.slice(0, url.indexOf('#'));
		var file = path.normalize(rootPaths.web + '/' + url);
		console.log(file);
		return new protocol.RequestFileJob(file);
	});
});

require(rootPaths.app + "/index");

if (process.env['CHILD']) {
    console.log(process.argv);
    child_process.spawn(process.env['CHILD'], [], { env: { "PIPE": rpc.getName() } });
}
