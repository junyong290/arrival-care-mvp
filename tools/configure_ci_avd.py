"""Set physical portrait geometry before the CI emulator starts."""
from pathlib import Path
import os
base=Path(os.environ.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))
config=base/'ArrivalCarePortrait.avd/config.ini'
assert config.is_file(),str(config)
settings={'hw.lcd.width':'720','hw.lcd.height':'1280','hw.lcd.density':'320','hw.initialOrientation':'portrait'}
lines=[line for line in config.read_text().splitlines() if line.partition('=')[0].strip() not in settings]
config.write_text('\n'.join(lines+[f'{k}={v}' for k,v in settings.items()])+'\n')
print('Configured physical portrait: 720x1280 at 320dpi')
