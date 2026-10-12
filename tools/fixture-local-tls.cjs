'use strict';
// Synthetic peer only. Ephemeral test key/certificate arrive on stdin, never argv/files/logs.
const tls = require('node:tls');
let setup = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => { setup += chunk; if (setup.length > 16384) process.exit(2); });
process.stdin.on('end', () => {
  const config = JSON.parse(setup);
  setup = '';
  const body = JSON.stringify({ periods: { today: { totalTokens: 123 } }, devices: [] });
  const counts = { requests: 0, streams: 0, active: 0, closed: 0, wrongHeaders: 0 };
  let firstAuth;
  const emit = () => process.stdout.write(JSON.stringify({ type: 'counts', ...counts }) + '\n');
  const server = tls.createServer({ key: config.key, cert: config.cert }, socket => {
    let buffer = '', parsed = false, streaming = false;
    socket.on('error', () => socket.destroy());
    socket.on('close', () => { if (streaming) { counts.active--; counts.closed++; emit(); } });
    socket.on('data', chunk => {
      if (parsed) return;
      buffer += chunk.toString('utf8');
      if (buffer.length > 16384) { socket.destroy(); return; }
      if (!buffer.includes('\r\n\r\n')) return;
      parsed = true;
      const lines = buffer.split('\r\n');
      const path = lines[0].split(' ')[1];
      if (!['/api/health', '/api/stats', '/api/stats/stream'].includes(path)) { socket.destroy(); return; }
      const authLine = lines.find(line => /^authorization:/i.test(line));
      const auth = authLine?.slice(authLine.indexOf(':') + 1).trim();
      if (path === '/api/health') { if (auth) counts.wrongHeaders++; }
      else {
        if (!/^Bearer [0-9a-f-]{36}$/i.test(auth || '')) counts.wrongHeaders++;
        if (!firstAuth) firstAuth = auth;
        else if (auth !== firstAuth) counts.wrongHeaders++;
      }
      buffer = '';
      counts.requests++;
      if (path === '/api/stats/stream') {
        streaming = true;
        counts.streams++; counts.active++;
        socket.write('HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {"stats":' + body + '}\n\n');
      } else {
        const response = path === '/api/health' ? JSON.stringify({ ok: true, role: 'hub' }) : body;
        socket.end('HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ' + Buffer.byteLength(response) + '\r\nConnection: close\r\n\r\n' + response);
      }
      emit();
    });
  });
  server.on('tlsClientError', () => {}); // Expected untrusted/wrong-host cancellation; no private diagnostics.
  server.listen(0, '127.0.0.1', () => process.stdout.write(JSON.stringify({ type: 'ready', port: server.address().port }) + '\n'));
});
