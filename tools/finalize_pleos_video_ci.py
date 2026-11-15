"""Finalize an actual Pleos recording in CI without generating or replacing UI."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

APK_SHA = '2c1bfad093e3333dec221d839104190b155af7e7fff5bf32c019d68e743d034e'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--evidence', required=True, type=Path)
args = parser.parse_args()
folder = args.evidence
record = json.loads((folder / 'video-timeline.json').read_text(encoding='utf-8'))
assert record['verified'] and record['apk_sha256'] == APK_SHA
raw_name = record['file']
assert Path(raw_name).name == raw_name, 'Expected recording basename'
raw = folder / raw_name
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
assert digest(raw) == record['sha256'], 'Raw recording checksum mismatch'


def run(command, timeout=240):
    process = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
    assert process.returncode == 0, process.stderr[-4000:]
    return process


def probe(path):
    result = run(['ffprobe', '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(path)], 30)
    metadata = json.loads(result.stdout)
    video = next(s for s in metadata['streams'] if s.get('codec_type') == 'video')
    return float(metadata['format']['duration']), int(video['width']), int(video['height']), metadata


duration, width, height, original_probe = probe(raw)
assert 1 < duration < 180 and width == 1920 and height == 1080
target = folder / 'arrivalcare-pleos-0.3.1-demo.mp4'
speed = 1.0
base = ['ffmpeg', '-hide_banner', '-v', 'error', '-y', '-i', str(raw), '-map', '0:v:0', '-an']
if 60 <= duration <= 90:
    command = base + ['-c:v', 'copy', '-movflags', '+faststart', str(target)]
    processing = 'MP4 stream copy with faststart; complete original timeline, no cuts, no speed change, no generated UI.'
else:
    target_duration = 88.0 if duration > 90 else 64.0
    speed = duration / target_duration
    font = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    assert font.is_file(), 'Banner font is required for retimed footage'
    banner = f'ACTUAL PLEOS 2.1.0 | v0.3.1 | {speed:.2f}x replay | DEMO map/route'
    filters = (f'setpts=(PTS-STARTPTS)/{speed:.10f},fps=30,'
               'drawbox=x=0:y=0:w=iw:h=44:color=black@0.82:t=fill,'
               f"drawtext=fontfile={font}:text='{banner}':x=16:y=10:fontsize=22:fontcolor=white")
    command = base + ['-vf', filters, '-filter_threads', '1', '-c:v', 'libx264', '-threads', '2',
                      '-preset', 'veryfast', '-crf', '22', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(target)]
    processing = (f'Complete actual recording uniformly retimed at {speed:.6f}x with visible replay-speed banner; '
                  'no cuts or generated UI; H.264 CRF22, yuv420p, faststart.')
run(command)
final_duration, final_width, final_height, final_probe = probe(target)
assert 59.9 <= final_duration <= 90.1, final_duration
decode = run(['ffmpeg', '-hide_banner', '-v', 'error', '-threads', '2', '-i', str(target), '-f', 'null', '-'])
assert not decode.stderr.strip(), 'Decode warnings/errors: ' + decode.stderr[-4000:]
(folder / 'final-video-decode.txt').write_text(decode.stderr, encoding='utf-8')
(folder / 'final-video-ffprobe.json').write_text(json.dumps(final_probe, indent=2), encoding='utf-8')
review = folder / 'video-review'
review.mkdir(exist_ok=True)
frames = []
for stage in ['arrival_plan', 'exit_confirmation_required', 'parking_route_approval', 'reunion', 'journey_complete']:
    mark = next(mark for mark in record['timeline'] if mark['stage'] == stage)
    second = min(max(float(mark['elapsed_seconds']) / speed - 0.2, 0), final_duration - 0.2)
    filename = stage + '.png'
    run(['ffmpeg', '-hide_banner', '-v', 'error', '-y', '-ss', f'{second:.3f}', '-i', str(target),
         '-frames:v', '1', '-threads', '2', str(review / filename)], 45)
    assert (review / filename).stat().st_size > 0
    frames.append({'stage': stage, 'video_second': round(second, 3), 'file': 'video-review/' + filename})
metadata = {
    'file': target.name, 'apk_sha256': APK_SHA, 'sha256': digest(target),
    'source_recording_sha256': record['sha256'], 'source_duration_seconds': duration,
    'duration_seconds': final_duration, 'width': final_width, 'height': final_height,
    'decode_verified': True, 'processing': processing, 'playback_speed': speed,
    'actual_pleos_capture': True, 'generated_ui_frames': False, 'review_frames': frames,
}
(folder / 'final-video.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(metadata, indent=2), flush=True)
