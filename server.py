#!/usr/bin/env python3
"""
AeroTrack Local Server
Run with: python3 server.py
"""

import http.server
import socketserver
import os
import sys
import webbrowser

PORT = 8080
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        # Enable CORS and caching headers for local testing
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        super().end_headers()

def main():
    os.chdir(DIRECTORY)
    for p in range(PORT, PORT + 20):
        try:
            with socketserver.TCPServer(("", p), Handler) as httpd:
                url = f"http://localhost:{p}/index.html"
                print("=" * 60)
                print(f"🚀 AeroTrack Running Visualizer is running at:")
                print(f"   {url}")
                print(f"   Workout file: exercise_tcx_file.tcx pre-loaded!")
                print("   Press Ctrl+C to stop the server.")
                print("=" * 60)
                
                # Try opening the browser automatically
                try:
                    webbrowser.open(url)
                except Exception:
                    pass
                
                httpd.serve_forever()
                break
        except OSError:
            continue

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nServer stopped.")
        sys.exit(0)
