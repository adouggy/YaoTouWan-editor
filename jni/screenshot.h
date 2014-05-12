//
//  screenshot.h
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-4.
//
//

#include <linux/fb.h>
#include <linux/kd.h>

typedef struct FBInfo {
    int fd;
	unsigned char *bits;
	struct fb_fix_screeninfo fi;
	struct fb_var_screeninfo vi;
} FBInfo;

int fb_width(FBInfo *fb);
int fb_height(FBInfo *fb);
int fb_bpp(FBInfo *fb);
int fb_size(FBInfo *fb);
int fb_pix_fmt(FBInfo *fb);
int fb_open(FBInfo *fb);
void fb_close(FBInfo *fb);
