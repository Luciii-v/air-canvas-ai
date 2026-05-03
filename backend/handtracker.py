import cv2
import mediapipe as mp
import socket
import time
import math
import threading

# ── Threaded Video Stream ──────────────────────────────────────────────────
class VideoStream:
    def __init__(self, src=0):
        self.stream = cv2.VideoCapture(src)
        self.stream.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        self.stream.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
        (self.grabbed, self.frame) = self.stream.read()
        self.stopped = False

    def start(self):
        threading.Thread(target=self.update, args=(), daemon=True).start()
        return self

    def update(self):
        while True:
            if self.stopped: return
            (self.grabbed, self.frame) = self.stream.read()

    def read(self): return self.frame

    def stop(self):
        self.stopped = True
        self.stream.release()

# ── UDP bridge ────────────────────────────────────────────────────────────────
server_address = ('127.0.0.1', 5005)
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

# ── MediaPipe ─────────────────────────────────────────────────────────────────
mp_hands    = mp.solutions.hands
mp_drawing  = mp.solutions.drawing_utils
hands       = mp_hands.Hands(max_num_hands=1,
                             model_complexity=0,
                             min_detection_confidence=0.75,
                             min_tracking_confidence=0.75)

vs = VideoStream().start()

# ── State Tracking ────────────────────────────────────────────────────────────
EMA_ALPHA = 0.4
smooth_x, smooth_y = 0.0, 0.0
gesture_buffer = []
BUFFER_SIZE = 3
first_frame = True

# Stillness & Export Timer
palm_timer_start = 0
STILL_THRESHOLD = 8.0 # Max pixels of movement for 'stillness'
last_pos = (0, 0)

def get_dist(p1, p2):
    return math.hypot(p1.x - p2.x, p1.y - p2.y)

# ── Main loop ─────────────────────────────────────────────────────────────────
while True:
    image = vs.read()
    if image is None: continue

    image = cv2.flip(image, 1)
    results = hands.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))

    gesture = "HOVER"
    thickness = 0.5
    packet = ""

    if results.multi_hand_landmarks:
        for hand_lm in results.multi_hand_landmarks:
            # Finger detection
            l = hand_lm.landmark
            thumb_up  = l[4].x < l[3].x if l[5].x < l[17].x else l[4].x > l[3].x # Basic thumb detection
            index_up  = l[8].y  < l[6].y
            middle_up = l[12].y < l[10].y
            ring_up   = l[16].y < l[14].y
            pinky_up  = l[20].y < l[18].y

            # Thickness
            hand_ref = get_dist(l[0], l[9])
            thickness = min(2.0, max(0.2, get_dist(l[4], l[8]) / (hand_ref + 0.001)))

            # Gestures
            is_open_palm = index_up and middle_up and ring_up and pinky_up # Thumb is optional/extra
            
            if index_up and middle_up and not ring_up: raw_g = "ERASE"
            elif index_up and not middle_up: raw_g = "DRAW"
            else: raw_g = "HOVER"

            # Export logic (Open Palm + Stillness)
            curr_pos = (l[0].x * 640, l[0].y * 480)
            movement = math.hypot(curr_pos[0] - last_pos[0], curr_pos[1] - last_pos[1])
            last_pos = curr_pos

            if is_open_palm and movement < STILL_THRESHOLD:
                if palm_timer_start == 0: palm_timer_start = time.time()
                elif time.time() - palm_timer_start > 2.0:
                    raw_g = "EXPORT"
                    palm_timer_start = 0 # Reset after trigger
            else:
                palm_timer_start = 0

            gesture_buffer.append(raw_g)
            if len(gesture_buffer) > BUFFER_SIZE: gesture_buffer.pop(0)
            gesture = max(set(gesture_buffer), key=gesture_buffer.count)

            # Build Packet
            header = f"{gesture},{thickness:.2f}"
            lms = [f"{int(lm.x * 1280)},{int(lm.y * 720)}" for lm in l]
            sock.sendto(f"{header}|{'|'.join(lms)}".encode(), server_address)

    cv2.imshow("AI Canvas - Pro Tracker", image)
    if cv2.waitKey(1) & 0xFF == 27: break

vs.stop()
cv2.destroyAllWindows()