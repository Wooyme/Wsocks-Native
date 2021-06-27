#include <linux/fs.h>
#include <asm/segment.h>
#include <asm/uaccess.h>
#include <linux/buffer_head.h>

struct file *file_open(const char *path, int flags, int rights)
{
    struct file *filp = NULL;
    mm_segment_t oldfs;
    int err = 0;

    oldfs = get_fs();
    set_fs(get_ds());
    filp = filp_open(path, flags, rights);
    set_fs(oldfs);
    if (IS_ERR(filp)) {
        err = PTR_ERR(filp);
        return NULL;
    }
    return filp;
}
void file_close(struct file *filp)
{
    mm_segment_t oldfs;
    oldfs = get_fs();
    set_fs(get_ds());
    filp_close(filp, NULL);
    set_fs(oldfs);
}


size_t read_hex(struct file *filp,void *buf,loff_t offset,size_t len){
    char *hex_map = "0123456789ABCDEF";
    char byte;
    char hex[4] = {'\\','x',0,0};
    loff_t i;
    for(i=offset;i<=offset+len;i++){
    	if(kernel_read(filp,i,&byte,1)<=0){
	    break;
	}
	hex[2] = hex_map[byte / 16];
	hex[3] = hex_map[byte % 16];
	memcpy((i-offset)*4,hex,4);
    }
    return (i-offset)*4;
}
