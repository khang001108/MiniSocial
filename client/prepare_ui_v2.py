#!/usr/bin/env python3
"""Build compact MiniSocial Java ME runtime assets from full v2 design pack.
Usage: python3 client/prepare_ui_v2.py /path/to/MiniSocial_UI_Assets_JavaME_v2
Requires Pillow. Generated assets are committed to client/resources and embedded by build_j2me.py.
"""
import sys
from pathlib import Path
from PIL import Image, ImageOps
ROOT = Path(__file__).resolve().parent
src = Path(sys.argv[1]) if len(sys.argv)>1 else ROOT.parent/'design'/'MiniSocial_UI_Assets_JavaME_v2'
out = ROOT/'resources'/'assets'
if not src.exists():
    raise SystemExit('Không có bộ thiết kế v2: '+str(src))
profiles = {'e72': (16,20,28,(310,30),(95,55)), 'wide': (23,27,39,(616,44),(126,75)), 'e6': (23,27,44,(616,50),(135,78))}
icons = ('home chats groups profile settings clock bell moon edit lock delete share like comment send attach camera plus search add_friend file wifi back info logout paper_plane gallery menu check refresh phone video shield more')

def emit(source, dest, size=None, crop=False):
    if not source.is_file():
        raise FileNotFoundError(source)
    with Image.open(source) as orig:
        im=orig.convert('RGBA')
        if size:
            if crop:
                im=ImageOps.fit(im,size,method=Image.Resampling.LANCZOS,centering=(.5,.45))
            else:
                im=im.resize(size,Image.Resampling.LANCZOS)
        dest.parent.mkdir(parents=True,exist_ok=True)
        im.save(dest,format='PNG',optimize=True,compress_level=9)

for p,(icon,logo,avatar,cover,empty) in profiles.items():
    base=src/p; dst=out/p
    for name in icons.split():
        for color,from_color in [('blue','blue'),('white','white')]:
            emit(base/'icons'/from_color/(name+'.png'),dst/'icons'/color/(name+'.png'),(icon,icon))
    emit(base/'brand'/'app_icon.png',dst/'logo.png',(logo,logo))
    for i in range(1,7):
        emit(base/'avatars'/('avatar_%02d.png'%i),dst/'avatars'/('avatar_%02d.png'%i),(avatar,avatar))
    for name in ('default_profile','default_group'):
        emit(base/'avatars'/(name+'.png'),dst/'avatars'/(name+'.png'),(avatar,avatar))
    emit(base/'avatars'/'default_profile.png',dst/'profile_avatar.png', (45 if p=='e72' else 72,45 if p=='e72' else 72))
    for theme in ('light','dark'):
        for group,name in [('messenger','chat_empty'),('social_feed','empty_feed'),('profile','empty_friends'),('notifications','empty_notifications')]:
            emit(base/'v2'/group/theme/(name+'.png'),dst/theme/(name+'.png'),empty)
        emit(base/'v2'/'profile'/theme/'cover_banner.png',dst/theme/'cover.png',cover,crop=True)
        emit(base/'v2'/'settings'/theme/'toggle_on.png',dst/theme/'toggle_on.png',(36 if p=='e72' else 50,20 if p=='e72' else 27))
        emit(base/'v2'/'settings'/theme/'toggle_off.png',dst/theme/'toggle_off.png',(36 if p=='e72' else 50,20 if p=='e72' else 27))
        emit(base/'v2'/'system'/theme/'spinner_00.png',dst/theme/'spinner.png',(16 if p=='e72' else 22,16 if p=='e72' else 22))
        emit(base/'v2'/'messenger'/theme/'read_double.png',dst/theme/'read_double.png',(18 if p=='e72' else 24,11 if p=='e72' else 15))
print('Runtime PNG files:',len(list(out.rglob('*.png'))))
print('Runtime PNG bytes:',sum(f.stat().st_size for f in out.rglob('*.png')))
