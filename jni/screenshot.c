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
    return fb->vi.xres;
}

int fb_height(FBInfo *fb)
{
    return fb->vi.yres;
}

int fb_bpp(FBInfo *fb)
{
    return fb->vi.bits_per_pixel>>3;
}

int fb_size(FBInfo *fb)
{
    return fb_width(fb) * fb_height(fb) * fb_bpp(fb);
}

int fb_pix_fmt(FBInfo *fb) {
    if (fb_bpp(fb) == 2) {
        return PIX_FMT_RGB565;
     } else if (fb_bpp(fb) == 3) {
         return PIX_FMT_RGB24;
     } else if (fb_bpp(fb) == 4) {
         return PIX_FMT_BGRA;
     }
//    if (pix_fmt == 0) {
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
//    }
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

	fb->bits = mmap(0, fb->fi.smem_len, PROT_READ | PROT_WRITE, MAP_SHARED, fb->fd, 0);
    
	if (fb->bits == MAP_FAILED) {
	    LOGE("fb open failed, mmap error");
		goto fail;
	}

	return 0;
    
fail:
	close(fb->fd);
    
	return -1;
}

void fb_close(FBInfo *fb)
{
	munmap(fb->bits, fb->fi.smem_len);
	close(fb->fd);
}

void log_fb(FBInfo *fb)
{
    LOGI("fb->vi.xres = %u", fb->vi.xres);
    LOGI("fb->vi.yres = %u", fb->vi.yres);
    LOGI("fb->vi.xres_virtual = %u", fb->vi.xres_virtual);
    LOGI("fb->vi.yres_virtual = %u", fb->vi.yres_virtual);
    LOGI("fb->vi.xoffset = %u", fb->vi.xoffset);
    LOGI("fb->vi.yoffset = %u", fb->vi.yoffset);
    LOGI("fb->vi.bits_per_pixel = %u", fb->vi.bits_per_pixel);
    LOGI("fb->vi.grayscale = %u", fb->vi.grayscale);

    LOGI("fb->vi.pixclock = %u", fb->vi.pixclock);
    LOGI("fb->vi.left_margin = %u", fb->vi.left_margin);
    LOGI("fb->vi.right_margin = %u", fb->vi.right_margin);
    LOGI("fb->vi.upper_margin = %u", fb->vi.upper_margin);
    LOGI("fb->vi.lower_margin = %u", fb->vi.lower_margin);
    LOGI("fb->vi.hsync_len = %u", fb->vi.hsync_len);
    LOGI("fb->vi.vsync_len = %u", fb->vi.vsync_len);
    LOGI("fb->vi.sync = %u", fb->vi.sync);
    LOGI("fb->vi.vmode = %u", fb->vi.vmode);
    LOGI("fb->vi.rotate = %u", fb->vi.rotate);

    LOGI("fb->fi.smem_len = %u", fb->fi.smem_len);
    LOGI("fb->fi.type = %u", fb->fi.type);
    LOGI("fb->fi.type_aux = %u", fb->fi.type_aux);
    LOGI("fb->fi.visual = %u", fb->fi.visual);
    LOGI("fb->fi.xpanstep = %u", fb->fi.xpanstep);
    LOGI("fb->fi.ypanstep = %u", fb->fi.ypanstep);
    LOGI("fb->fi.ywrapstep = %u", fb->fi.ywrapstep);
    LOGI("fb->fi.line_length = %u", fb->fi.line_length);
    LOGI("fb->fi.mmio_start = %lu", fb->fi.mmio_start);
    LOGI("fb->fi.mmio_len = %u", fb->fi.mmio_len);
    LOGI("fb->fi.accel = %u", fb->fi.accel);
}