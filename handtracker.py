import cv2
import mediapipe as mp
import socket
import time
import math

# ── UDP bridge ────────────────────────────────────────────────────────────────
server_address = ('127.0.0.1', 5005)
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

# ── MediaPipe ─────────────────────────────────────────────────────────────────
mp_hands    = mp.solutions.hands
mp_drawing  = mp.solutions.drawing_utils
hands       = mp_hands.Hands(max_num_hands=1,
                             min_detection_confidence=0.7,
                             min_tracking_confidence=0.7)

# Drawing specs for skeleton — zero memory allocation per frame
LANDMARK_STYLE   = mp_drawing.DrawingSpec(color=(0, 255, 255), thickness=2, circle_radius=3)
CONNECTION_STYLE = mp_drawing.DrawingSpec(color=(180, 255, 255), thickness=1)

cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH,  640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

# ── Smoothing & deadzone ──────────────────────────────────────────────────────
EMA_ALPHA = 0.25
DEADZONE  = 6

smooth_x, smooth_y       = 0.0, 0.0
prev_sent_x, prev_sent_y = -1, -1
prev_gesture              = ""
first_frame               = True

# ── FPS ───────────────────────────────────────────────────────────────────────
fps_timer   = time.time()
fps         = 0
frame_count = 0

print("Python AI Brain started. Use 1 finger to Draw, 2 to Hover, 3 to Erase.")


def get_spread_pct(landmarks):
    i_tip = landmarks.landmark[8]
    p_tip = landmarks.landmark[20]
    wrist = landmarks.landmark[0]
    i_mcp = landmarks.landmark[5]
    spread = math.hypot(i_tip.x - p_tip.x, i_tip.y - p_tip.y)
    ref    = math.hypot(wrist.x - i_mcp.x, wrist.y - i_mcp.y)
    return min(100, int((spread / max(ref * 3.0, 0.001)) * 100))


# ── Main loop ─────────────────────────────────────────────────────────────────
while cap.isOpened():
    success, image = cap.read()
    if not success:
        continue

    frame_count += 1
    now = time.time()
    if now - fps_timer >= 1.0:
        fps = frame_count; frame_count = 0; fps_timer = now

    image   = cv2.flip(image, 1)
    h, w    = image.shape[:2]
    results = hands.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))

    hands_detected = 0
    gesture        = "HOVER"
    spread_pct     = 0

    if results.multi_hand_landmarks:
        for hand_lm in results.multi_hand_landmarks:
            hands_detected += 1

            # Lightweight skeleton — single mp_drawing call, no numpy allocation
            mp_drawing.draw_landmarks(image, hand_lm,
                                      mp_hands.HAND_CONNECTIONS,
                                      LANDMARK_STYLE, CONNECTION_STYLE)

            # Gesture
            index_up  = hand_lm.landmark[8].y  < hand_lm.landmark[6].y
            middle_up = hand_lm.landmark[12].y < hand_lm.landmark[10].y
            ring_up   = hand_lm.landmark[16].y < hand_lm.landmark[14].y
            gesture   = "DRAW"  if (index_up and not middle_up) else \
                        "ERASE" if (index_up and middle_up and ring_up) else "HOVER"

            spread_pct = get_spread_pct(hand_lm)

            # EMA smoothing
            raw_x = hand_lm.landmark[8].x * 1280
            raw_y = hand_lm.landmark[8].y * 720
            if first_frame:
                smooth_x, smooth_y = raw_x, raw_y; first_frame = False
            else:
                smooth_x += EMA_ALPHA * (raw_x - smooth_x)
                smooth_y += EMA_ALPHA * (raw_y - smooth_y)
            x, y = int(smooth_x), int(smooth_y)

            # Deadzone + gesture-change gate
            moved     = abs(x - prev_sent_x) > DEADZONE or abs(y - prev_sent_y) > DEADZONE
            g_changed = gesture != prev_gesture
            if moved or prev_sent_x == -1 or g_changed:
                sock.sendto(f"{x},{y},{gesture}".encode(), server_address)
                prev_sent_x, prev_sent_y = x, y
                prev_gesture = gesture

            # Simple cursor dot on webcam view
            cmap  = {"DRAW": (60, 80, 255), "ERASE": (255, 255, 255), "HOVER": (0, 255, 140)}
            cx, cy = int(smooth_x * w / 1280), int(smooth_y * h / 720)
            cv2.circle(image, (cx, cy), 12, cmap.get(gesture, (0, 255, 0)), 2)
            cv2.circle(image, (cx, cy),  4, (255, 255, 255), -1)

    # Minimal HUD — plain text, zero memory cost
    font = cv2.FONT_HERSHEY_SIMPLEX
    cmap2 = {"DRAW": (60, 80, 255), "ERASE": (255, 255, 255), "HOVER": (0, 210, 180)}
    cv2.putText(image, f"Hands: {hands_detected}",   (10, 22), font, 0.5, (0, 255, 180), 1)
    cv2.putText(image, f"FPS:   {fps:02d}",           (10, 42), font, 0.5, (160, 160, 160), 1)
    cv2.putText(image, f"Gesture: {gesture}",          (10, 62), font, 0.5, cmap2.get(gesture, (160,160,160)), 1)
    cv2.putText(image, f"Spread:  {spread_pct}%",      (10, 82), font, 0.5, (160, 160, 160), 1)

    cv2.imshow("Air Canvas - AI Tracker", image)
    if cv2.waitKey(1) & 0xFF == 27:
        break

cap.release()
cv2.destroyAllWindows()