from html.parser import HTMLParser
from pathlib import Path
import argparse

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


UI_ROOT = Path(__file__).resolve().parents[1]
args_parser = argparse.ArgumentParser(description="Build the current iTantra review guide")
args_parser.add_argument("--output", type=Path, default=UI_ROOT / "review" / "itantra_ideal_product_review_guide.pdf")
args = args_parser.parse_args()
SITE = UI_ROOT / "current-prototype" / "dist" / "index.html"
OUT = args.output.resolve()
OUT.parent.mkdir(parents=True, exist_ok=True)

import reportlab
font_pairs = [
    (Path(r"C:\Windows\Fonts\arial.ttf"), Path(r"C:\Windows\Fonts\arialbd.ttf")),
    (Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")),
    (Path(reportlab.__file__).parent / "fonts" / "Vera.ttf", Path(reportlab.__file__).parent / "fonts" / "VeraBd.ttf"),
]
regular_font, bold_font = next((pair for pair in font_pairs if all(p.is_file() for p in pair)))
pdfmetrics.registerFont(TTFont("Arial", str(regular_font)))
pdfmetrics.registerFont(TTFont("Arial-Bold", str(bold_font)))


class AssumptionParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_section = False
        self.in_item = False
        self.items = []
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "section" and ("id", "assumptions-title") in attrs:
            self.in_section = True
        elif self.in_section and tag == "li":
            self.in_item = True
            self.parts = []

    def handle_endtag(self, tag):
        if tag == "li" and self.in_item:
            self.items.append("".join(self.parts).strip())
            self.in_item = False
        elif tag == "section" and self.in_section:
            self.in_section = False

    def handle_data(self, data):
        if self.in_item:
            self.parts.append(data)


parser = AssumptionParser()
parser.feed(SITE.read_text(encoding="utf-8"))
ASSUMPTIONS = parser.items
assert len(ASSUMPTIONS) == 7, ASSUMPTIONS

W, H = A4
M = 43
CW = W - 2 * M
INK = colors.HexColor("#14243A")
MUTED = colors.HexColor("#526779")
TEAL = colors.HexColor("#087B83")
PALE = colors.HexColor("#EAF6F5")
LINE = colors.HexColor("#AABBC6")

c = canvas.Canvas(str(OUT), pagesize=A4, pageCompression=1)
c.setTitle("iTantra complete product website review guide")
c.setAuthor("iTantra")
c.setSubject("Printable and fillable guide for reviewing the ideal-product website simulation")


def wrap(text, width, font="Arial", size=9.7):
    words = text.split()
    result, line = [], ""
    for word in words:
        trial = f"{line} {word}" if line else word
        if pdfmetrics.stringWidth(trial, font, size) <= width:
            line = trial
        else:
            if line:
                result.append(line)
            line = word
    if line:
        result.append(line)
    return result


def txt(text, x, y, size=10, font="Arial", color=INK):
    c.setFillColor(color)
    c.setFont(font, size)
    c.drawString(x, y, text)


def para(text, x, y, width, size=9.7, leading=13.2, color=INK, font="Arial"):
    for line in wrap(text, width, font, size):
        txt(line, x, y, size, font, color)
        y -= leading
    return y


def section(label, y):
    c.setFillColor(PALE)
    c.roundRect(M, y - 10, CW, 26, 6, fill=1, stroke=0)
    txt(label, M + 10, y - 1, 11, "Arial-Bold", TEAL)
    return y - 29


def page_head(kicker, title, number):
    c.setFillColor(TEAL)
    c.rect(0, H - 9, W, 9, fill=1, stroke=0)
    txt(kicker.upper(), M, H - 38, 8, "Arial-Bold", TEAL)
    txt(title, M, H - 67, 20, "Arial-Bold")
    c.setStrokeColor(LINE)
    c.line(M, H - 78, W - M, H - 78)
    c.line(M, 31, W - M, 31)
    txt("iTantra  |  Ideal-product website review", M, 19, 8, color=MUTED)
    txt(f"{number} / 6", W - M - 26, 19, 8, color=MUTED)
    return H - 100


def task(number, title, trying, notice, y, gap=19):
    txt(f"{number:02d}", M, y, 13, "Arial-Bold", TEAL)
    txt(title, M + 30, y, 12, "Arial-Bold")
    y -= 19
    txt("What to try", M + 30, y, 9.2, "Arial-Bold", MUTED)
    y -= 14
    y = para(trying, M + 30, y, CW - 30, 10.3, 14.2)
    y -= 2
    txt("What to notice", M + 30, y, 9.2, "Arial-Bold", MUTED)
    y -= 14
    y = para(notice, M + 30, y, CW - 30, 10.3, 14.2)
    return y - gap


def checkbox(name, x, y, label, small=False):
    size = 10 if small else 11
    c.acroForm.checkbox(
        name=name, tooltip=label, x=x, y=y - 2, size=size,
        buttonStyle="check", borderColor=TEAL, fillColor=colors.white,
        textColor=TEAL, forceBorder=True,
    )
    txt(label, x + size + 4, y, 8.2 if small else 9)


def field(name, x, y, width, height, tooltip, multiline=False):
    c.acroForm.textfield(
        name=name, tooltip=tooltip, x=x, y=y, width=width, height=height,
        fontName="Helvetica", fontSize=9, textColor=INK,
        borderColor=LINE, fillColor=colors.white, borderWidth=0.7,
        borderStyle="solid", fieldFlags="multiline" if multiline else "",
        forceBorder=True,
    )


def assumption(number, wording, top):
    height = 79
    bottom = top - height
    c.setStrokeColor(LINE)
    c.roundRect(M, bottom, CW, height, 5, fill=0, stroke=1)
    txt(f"{number}.", M + 10, top - 15, 9.3, "Arial-Bold", TEAL)
    after = para(wording, M + 28, top - 15, CW - 40, 9, 11.5)
    choice_y = bottom + 38
    assert after >= choice_y, (number, after, choice_y)
    checkbox(f"assumption_{number}_accept", M + 11, choice_y, "Accept", True)
    checkbox(f"assumption_{number}_change", M + 91, choice_y, "Change", True)
    checkbox(f"assumption_{number}_unsure", M + 176, choice_y, "Unsure", True)
    txt("Reason / change:", M + 11, bottom + 17, 8.2, color=MUTED)
    field(f"assumption_{number}_reason", M + 109, bottom + 7, CW - 120, 21,
          f"Reason for assumption {number}")
    return bottom - 5


WIDTHS = [68, 95, 105, 105, CW - 373]
HEADERS = ["Screen", "What I did", "What I expected", "What happened", "Severity"]


def table_header(top):
    c.setFillColor(PALE)
    c.rect(M, top - 25, CW, 25, fill=1, stroke=0)
    x = M
    for label, width in zip(HEADERS, WIDTHS):
        txt(label, x + 5, top - 16, 8.4, "Arial-Bold", TEAL)
        x += width
    return top - 25


def table_row(number, top, height):
    bottom = top - height
    c.setStrokeColor(LINE)
    c.rect(M, bottom, CW, height, fill=0, stroke=1)
    x = M
    starts = []
    for width in WIDTHS:
        starts.append(x)
        x += width
        if x < M + CW - 1:
            c.line(x, top, x, bottom)
    for col in range(4):
        field(f"feedback_{number}_{col}", starts[col] + 4, bottom + 5,
              WIDTHS[col] - 8, height - 10,
              f"Feedback row {number}, {HEADERS[col]}", True)
    for idx, label in enumerate(["blocks me", "annoying", "nitpick"]):
        checkbox(f"feedback_{number}_severity_{idx}", starts[4] + 7,
                 top - 20 - 23 * idx, label, True)
    return bottom


# Page 1: context, use instructions, and first setup steps.
y = page_head("For someone new to the project", "Review guide", 1)
y = section("1  What this is", y)
for sentence in [
    "iTantra is a planned Android app for short messages during a disaster when ordinary phone service may be unavailable.",
    "It can carry a private message toward a trusted contact through other nearby phones, even when that contact is far away.",
    "It also lets someone request help through SOS or choose to respond to a nearby request.",
]:
    y = para(sentence, M, y, CW, 9.6, 13.1) - 3
y -= 2
y = para("The website is a simulation with fake contacts, speech, radio delivery, QR scans, timing, and replies. It is not the real app.", M, y, CW, 9.6, 13.1) - 5
y = para("Judge whether it feels right, what confuses you, and what seems missing. Ignore speech accuracy, real network behavior, and exact pixel polish.", M, y, CW, 9.6, 13.1) - 9
y = section("2  How to use the site", y)
url = "https://itantra-app-flow-review.kotesarajveer.chatgpt.site/"
txt("Open:", M, y, 9.6, "Arial-Bold")
txt(url, M + 34, y, 9.1, color=TEAL)
c.linkURL(url, (M + 34, y - 3, W - M, y + 11), relative=0)
y -= 19
y = para("Use the phone frame like the app. The controls beside it (or below it on a narrow screen) trigger fake events and failures. They are not part of the app. Start with Reset full review; confirm the reset.", M, y, CW, 9.5, 12.8) - 10
y = section("3  Guided walkthrough", y)
y = task(1, "First launch and readiness", "Tap Nearby devices, Notifications, Microphone, and Camera so each says Allowed. Tap Enter iTantra. Open Readiness from Home, then return Home.", "Can you tell what is ready and what would stop a feature?", y)
y = task(2, "Exchange a trusted identity", "Open Contacts, then My identity to see your QR. Return to Contacts. Tap Scan a contact QR; leave Next QR scan on New identity and tap Simulate scan. Tick the identity-check box and add Rahul.", "The QR creates a contact only after you confirm it. Does that feel safe and understandable?", y)
assert y > 42, y
c.showPage()

# Page 2: private messages, voice, failure, receive, and draft behavior.
y = page_head("Continue in the same review session", "Walkthrough: messages", 2)
y = task(3, "Speak, choose a recipient, send", "On Home, look through Message language (all 10 choices). Hold Hold to talk, release, and wait for the draft. Edit it, choose Rahul as Trusted recipient, switch on Urgent message, then Send message.", "Speech opens an editable draft. Sending requires a trusted recipient and puts the message in Logs as Queued.", y)
y = task(4, "Follow delayed delivery", "Open Logs and the message. In the controls, tap Relay accepts encrypted copy, then Recipient confirms delivery. Check the row or detail after each tap.", "Relayed means another phone carries it; Delivered means Rahul confirmed delivery. Rahul never had to be nearby.", y)
y = task(5, "Try Hands-free and voice failure", "Go Home > Hands-free. Tap Simulate speech in Hands-free beside the phone, then Done. Back out and Discard the draft. Set Next voice result to Transcription fails; try Hold to talk again, then Continue as text. Tap Back from the empty draft.", "Both voice paths use the same draft. A failed voice result offers a way to type. Restore Transcript ready after this.", y)
y = task(6, "Receive, read, and hear text", "Tap Receive once beside the phone. See the Logs badge, open Logs, then open the new received row. Tick Next speech playback fails and tap Play aloud. Try Receive duplicate copy as well.", "Opening Logs alone leaves the item unread; opening the row marks it read. Speech failure leaves the text. A duplicate adds no second row.", y)
y = task(7, "Back out of a typed draft", "Return Home > Text. Type a few words. Tap Back, then Cancel. Tap Back again, then Discard.", "Cancel keeps the draft; Discard removes it. Is the warning clear?", y)
assert y > 42, y
c.showPage()

# Page 3: SOS roles and degraded states.
y = page_head("Use the controls to create remote events", "Walkthrough: SOS and recovery", 3)
y = task(8, "Respond to someone else's SOS", "Open Emergency, tap Start Emergency Mode, then turn on Available to help. Tap Incoming SOS offer beside the phone. Review the limited details and tap Accept. Type a reply and Send. Try Codes match, confirm, then End SOS session.", "Before acceptance, no name or full note is shown. The session starts encrypted but unverified; matching codes verifies the nearby device, not a person's role.", y)
y = task(9, "Request help through relays", "From Home, tap Request help · SOS. Choose a category and language, then Start SOS search; confirm the dialog. Tap No answer · widen search, then Responder accepts through relay. Tap Receive SOS session reply, then Play latest reply aloud.", "The search widens without a nearby answer. Relayed replies may be delayed, and Codes match is unavailable when the other person is not nearby.", y)
y = task(10, "Test SOS endings", "End that session. From Home, start another SOS and tap Two responders accept. End it, start another, then tap SOS / offer expires. Tap Back to home, start one more SOS, and use Cancel SOS. Confirm the dialog.", "Only the first responder connects. Expiry and cancellation have different explanations. Does each ending feel clear?", y)
y = task(11, "Check degraded service and identity loss", "In the controls, choose Bluetooth off under Apply condition and tap Apply condition. Open Readiness, then restore with All ready · no internet. In Readiness & identity, tap Simulate reinstall / identity loss and confirm. Open Contacts and Rahul.", "Saved messages stay visible. The old trusted contact cannot receive a new private message until a fresh QR exchange.", y)
assert y > 42, y
c.showPage()

# Page 4: exact wording from the website.
y = page_head("Wording copied from the live site's panel", "4  My assumptions", 4)
y = para("Tick one box for each item. For Change or Unsure, say what you would prefer. These seven statements match the site's My assumptions list word for word.", M, y, CW, 9.3, 12.7) - 10
for idx, wording in enumerate(ASSUMPTIONS, 1):
    y = assumption(idx, wording, y)
assert y > 42, y
c.showPage()

# Page 5: issue table with large printable / fillable cells.
y = page_head("One specific issue per row", "5  Feedback table", 5)
y = para("Name the screen, what you did, what you expected, and what happened. Tick one severity level. If you can, repeat the action before writing it down.", M, y, CW, 9.7, 13.5) - 13
y = table_header(y)
for row in range(1, 5):
    y = table_row(row, y, 143)
assert y > 42, y
c.showPage()

# Page 6: more issue rows and closing questions.
y = page_head("Finish with your overall impression", "Feedback and final questions", 6)
y = table_header(y)
for row in range(5, 7):
    y = table_row(row, y, 112)
y -= 20
y = section("6  Overall questions", y)
for idx, question in enumerate([
    "Which screen felt best, and why?",
    "Which screen felt worst, and why?",
    "What felt missing?",
    "What felt unnecessary?",
], 1):
    txt(question, M, y, 10, "Arial-Bold")
    y -= 58
    field(f"overall_{idx}", M, y, CW, 42, question, True)
    y -= 12
assert y > 42, y
c.save()
print(OUT)
