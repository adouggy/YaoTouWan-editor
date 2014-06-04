//
//  screenshot.c
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-4.
//
//

#include "screenshot.h"
#include <libavutil/pixfmt.h>
#include <fcntl.h>
#include <sys/mman.h>
#include "log.h"

int fb_width(FBInfo *fb)
{
    static int fb_width = 0;
    if (fb_width == 0) {
        fb_width = fb->vi.xres;
    }
    return fb_width;
}

int fb_height(FBInfo *fb)
{
    static int fb_height = 0;
    if (fb_height == 0) {
        fb_height = fb->vi.yres;
    }
    return fb_height;
}

int fb_bpp(FBInfo *fb)
{
    static int bits_per_pixel = 0;
    if (bits_per_pixel == 0) {
        bits_per_pixel = fb->vi.bits_per_pixel>>3;
    }
    return bits_per_pixel;
}

int fb_size(FBInfo *fb)
{
    static int size = 0;
    if (size == 0) {
        size = fb_width(fb) * fb_height(fb) * fb_bpp(fb);
    }
    return size;
}

int fb_pix_fmt(FBInfo *fb) {
    static int pix_fmt = 0;
    if (pix_fmt == 0) {
        if (fb_bpp(fb) == 2) {
            pix_fmt = PIX_FMT_RGB565;
        } else if (fb_bpp(fb) == 3) {
            pix_fmt = PIX_FMT_RGB24;
        } else if (fb_bpp(fb) == 4) {
            pix_fmt = PIX_FMT_BGRA;
        }
//        int ao = fb->alpha_offset;
//        int ro = fb->red_offset;
//        int go = fb->green_offset;
//        int bo = fb->blue_offset;
//        if (fb_bpp(fb) == 2)
//            return PIX_FMT_RGB565;
//        if (fb_bpp(fb) == 3)
//            return PIX_FMT_RGB24;
//
//        /* TODO: validate */
//        if (ao == 0 && ro == 8)
//            pix_fmt = PIX_FMT_ARGB;
//
//        if (ao == 0 && ro == 24 && go == 16 && bo == 8)
//            pix_fmt = PIX_FMT_RGBA;
//
//        if (ao == 0 && bo == 8)
//            pix_fmt = PIX_FMT_ABGR;
//
//        if (ro == 0)
//            pix_fmt = PIX_FMT_RGBA;
//
//        if (bo == 0)
//            pix_fmt = PIX_FMT_BGRA;
//
//        /* fallback */
//        return PIX_FMT_ABGR;
    }
	return pix_fmt;
}

int fb_open(FBInfo *fb)
{
    static const char *fbfilename = "/dev/graphics/fb0";
    
	fb->fd = open(fbfilename, O_RDWR);
    
	if (fb->fd < 0) {
		LOGE("can't open %s\n", fbfilename);
		return -1;
	}
    
	if (ioctl(fb->fd, FBIOGET_FSCREENINFO, &fb->fi) < 0)
		goto fail;
    
	if (ioctl(fb->fd, FBIOGET_VSCREENINFO, &fb->vi) < 0)
		goto fail;
    
	fb->bits = mmap(0, fb_size(fb), PROT_READ | PROT_WRITE, MAP_SHARED, fb->fd, 0);
    
	if (fb->bits == MAP_FAILED)
		goto fail;
    
	return 0;
    
fail:
	close(fb->fd);
    
	return -1;
}

void fb_close(FBInfo *fb)
{
	munmap(fb->bits, fb_size(fb));
	close(fb->fd);
    
	return;
}

