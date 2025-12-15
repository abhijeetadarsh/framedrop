import tkinter as tk
from tkinter import filedialog
import segno
import io
import os
import struct
import zlib
import random
import threading
import sys

# --- CONFIGURATION ---
CHUNK_SIZE = 400     # Safe size. Increase to 400 for higher speed if camera is good.
MAX_FPS = 30         # Target speed (Actual speed depends on CPU generation time)
MIN_FPS = 10         # Minimum random speed
SCALE = 6

class ShotgunSenderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Adaptive Speed Sender (Live Render)")
        self.root.geometry("600x650")
        
        self.file_blocks = []
        self.is_streaming = False
        self.packet_order = []
        self.current_idx = 0
        self.qr_version = None # To lock QR size
        
        # Adaptive Logic State
        self.cycle_count = 0
        self.current_cycle_fps = MAX_FPS
        
        # --- UI ELEMENTS ---
        tk.Button(root, text="Select File", command=self.select_file, height=2).pack(pady=10)
        self.lbl_status = tk.Label(root, text="Waiting...", font=("Arial", 12))
        self.lbl_status.pack()
        self.qr_label = tk.Label(root)
        self.qr_label.pack(expand=True)
        self.btn_start = tk.Button(root, text="Start Stream", command=self.toggle_stream, state="disabled", bg="gray")
        self.btn_start.pack(pady=10)

        # --- EXIT LOGIC ---
        # 1. Bind 'q' key on the GUI Window
        self.root.bind('<q>', lambda e: self.force_exit())
        self.root.protocol("WM_DELETE_WINDOW", self.force_exit)
        
        # 2. Start Background Thread for Terminal Input
        self.exit_thread = threading.Thread(target=self.terminal_listener, daemon=True)
        self.exit_thread.start()

    def terminal_listener(self):
        """Listens for 'q' in the terminal to kill app instantly."""
        print("--- CONTROLS ---")
        print("Press 'q' + Enter in this terminal to exit instantly.")
        print("Press 'Ctrl+C' to exit instantly.")
        print("------------------")
        while True:
            try:
                user_input = input()
                if user_input.strip().lower() == 'q':
                    self.force_exit()
            except (EOFError, KeyboardInterrupt):
                self.force_exit()

    def force_exit(self):
        """Nuclear option: Kills process immediately."""
        print("\nExiting immediately...")
        os._exit(0)

    def select_file(self):
        path = filedialog.askopenfilename()
        if not path: return
        
        # 1. Prepare Data
        try:
            raw = open(path, "rb").read()
            fname = os.path.basename(path).encode('utf-8')[:255]
            payload = len(fname).to_bytes(1, 'big') + fname + raw
            full_data = struct.pack('>I', len(payload)) + payload
            
            # 2. Split
            self.file_blocks = []
            for i in range(0, len(full_data), CHUNK_SIZE):
                chunk = full_data[i:i+CHUNK_SIZE]
                header = struct.pack('>II', i // CHUNK_SIZE, (len(full_data) + CHUNK_SIZE - 1) // CHUNK_SIZE)
                crc = struct.pack('>I', zlib.crc32(header + chunk) & 0xFFFFFFFF)
                self.file_blocks.append(header + chunk + crc)

            # Calculate the Version needed for the biggest block (the first one)
            # and force all subsequent blocks to match this size.
            if self.file_blocks:
                temp_qr = segno.make(self.file_blocks[0], error='M', micro=False)
                self.qr_version = temp_qr.version
                print(f"Locked QR Version to: {self.qr_version}")
                
            self.lbl_status.config(text=f"Loaded: {len(self.file_blocks)} blocks")
            self.btn_start.config(state="normal", bg="green")
        except Exception as e:
            print(f"Error loading file: {e}")

    def toggle_stream(self):
        self.is_streaming = not self.is_streaming
        if self.is_streaming:
            self.btn_start.config(text="Stop", bg="red")
            self.cycle_count = 0
            self.start_new_cycle()
            self.stream_loop()
        else:
            self.btn_start.config(text="Start", bg="green")

    def start_new_cycle(self):
        self.packet_order = list(range(len(self.file_blocks)))
        random.shuffle(self.packet_order)
        self.current_idx = 0
        self.cycle_count += 1
        
        if self.cycle_count == 1:
            self.current_cycle_fps = MAX_FPS
            mode = "BLITZ (100%)"
        else:
            self.current_cycle_fps = -1 
            mode = "RANDOM CLEANUP"
            
        print(f"Cycle {self.cycle_count}: {mode}")
        
        # REMOVED: Pre-rendering loop.
        
    def stream_loop(self):
        if not self.is_streaming: return
        
        if self.current_idx >= len(self.packet_order):
            self.start_new_cycle()
            if not self.is_streaming: return # Check again after cycle reset
            
        block_id = self.packet_order[self.current_idx]
        
        # --- LIVE RENDER (Generates QR on the fly) ---
        data = self.file_blocks[block_id]
        buff = io.BytesIO()
        # Note: We must use the locked 'self.qr_version' here
        segno.make(data, error='L', version=self.qr_version, micro=False).save(buff, kind='png', scale=SCALE)
        img = tk.PhotoImage(data=buff.getvalue())
        # ---------------------------------------------

        self.qr_label.config(image=img)
        self.qr_label.image = img
        
        self.current_idx += 1
        
        # FPS Logic
        if self.current_cycle_fps == -1:
            fps = MAX_FPS
            # fps = # random.randint(MIN_FPS, MAX_FPS)
        else:
            fps = self.current_cycle_fps
            
        self.lbl_status.config(text=f"Cycle {self.cycle_count} | Packet {self.current_idx}/{len(self.file_blocks)}\nTarget Speed: {fps} FPS")

        self.root.after(int(1000/fps), self.stream_loop)

if __name__ == "__main__":
    root = tk.Tk()
    app = ShotgunSenderApp(root)
    root.mainloop()