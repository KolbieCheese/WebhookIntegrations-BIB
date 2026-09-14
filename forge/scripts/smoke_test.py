"""Test a packaged mod on a disposable Forge server (Python 3.9+, Java 17).

Usage: python smoke_test.py --server-dir <disposable installed Forge server> --java <java executable>
The server must have the release JAR in mods/ and an accepted eula.txt.
Overwrites this test server's webhook configuration. Never point at a live server.
"""
import argparse
import http.server
import json
import pathlib
import queue
import subprocess
import threading
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--server-dir', type=pathlib.Path, required=True)
    parser.add_argument('--java', required=True)
    args = parser.parse_args()
    directory = args.server_dir.resolve()
    received = []

    class Receiver(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            received.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
            self.send_response(204)
            self.end_headers()

        def log_message(self, *_args):
            pass

    receiver = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Receiver)
    threading.Thread(target=receiver.serve_forever, daemon=True).start()
    config = directory / 'config' / 'webhookintegrations.json'
    config.parent.mkdir(exist_ok=True)
    config.write_text(json.dumps({'webhooks': {'main': f'http://127.0.0.1:{receiver.server_port}/hook'},
                                  'templates': {'test': {'content': 'Online: $playersOnline$'}}}), encoding='utf-8')
    import os
    arguments = 'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
    process = subprocess.Popen([args.java, '-Xmx1G', f'@libraries/net/minecraftforge/forge/1.20.1-47.4.0/{arguments}', 'nogui'],
                               cwd=directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    lines = queue.Queue()
    log = []

    def read():
        for line in process.stdout:
            log.append(line)
            lines.put(line)

    threading.Thread(target=read, daemon=True).start()

    def wait_for(text, timeout=120):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if process.poll() is not None and lines.empty():
                raise AssertionError(f'Server exited before {text}')
            try:
                if text in lines.get(timeout=0.5):
                    return
            except queue.Empty:
                pass
        raise AssertionError(f'Timed out waiting for {text}')

    def command(text, expected):
        process.stdin.write(text + '\n')
        process.stdin.flush()
        wait_for(expected, 20)

    try:
        wait_for('Done (')
        command('wi status', 'configured targets=1')
        command('wi send main quoted "hello" $player$', 'Webhook message queued.')
        command('wi template test main', 'Webhook message queued.')
        command('wi disable', 'Webhooks disabled.')
        command('wi send main must-not-send', 'Message not queued.')
        command('wi enable', 'Webhooks enabled.')
        saved = config.read_text(encoding='utf-8')
        config.write_text('{broken', encoding='utf-8')
        command('wi reload', 'previous settings retained')
        command('wi send main retained', 'Webhook message queued.')
        config.write_text(saved, encoding='utf-8')
        command('wi reload', 'Webhook configuration reloaded.')
        command('stop', 'Stopping the server')
        process.wait(timeout=40)
        assert process.returncode == 0
        contents = [payload.get('content') for payload in received]
        assert contents == ['Server started.', 'quoted "hello" $player$', 'Online: 0', 'retained', 'Server stopped.'], contents
        assert all(payload['allowed_mentions']['parse'] == [] for payload in received)
        print('PASS: packaged Forge mod loaded; startup/shutdown webhooks, commands, templates, disable/enable, reload recovery, and HTTP payloads verified.')
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=20)
        receiver.shutdown()
        (directory / 'smoke-test.log').write_text(''.join(log), encoding='utf-8')


if __name__ == '__main__':
    main()
