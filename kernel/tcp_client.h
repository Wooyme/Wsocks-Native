#include <linux/module.h>
#include <linux/net.h>
#include <net/sock.h>
#include <linux/tcp.h>
#include <linux/in.h>
#include <asm/uaccess.h>
#include <linux/socket.h>
#include <linux/slab.h>
#include <linux/time.h>
#include <linux/types.h>
#include <linux/socket.h>
#include <linux/errno.h>
#include <linux/mm.h>
#include <linux/slab.h>

#define UUID_LEN 36
u32 create_address(u8 *ip) {
    u32 addr = 0;
    int i;

    for(i=0; i<4; i++) {
        addr += ip[i];
        if(i==3)
            break;
        addr <<= 8;
    }
    return addr;
}
struct socket * create_socket(const char *ip,int port) {
    struct sockaddr_in saddr;
    int ret;
    struct socket *sock = 0;
    long arg;
    
    ret = sock_create(PF_INET, SOCK_STREAM, IPPROTO_TCP, &sock);
    if(ret < 0) {
        printk("Error: %d while creating first socket", ret);
        goto err;
    }
    memset(&saddr, 0, sizeof(saddr));
    saddr.sin_family = AF_INET;
    saddr.sin_port = htons(port);
    saddr.sin_addr.s_addr = htonl(create_address(ip));

    struct timeval timeout;
    timeout.tv_sec=0;
    timeout.tv_usec=0;
    
    sock_setsockopt(sock,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    

    ret = sock->ops->connect(sock, (struct sockaddr *)&saddr,sizeof(saddr), O_RDWR);
    
    if(ret && (ret != -EINPROGRESS)) {
        printk("Error: %d while connecting using conn", ret);
        goto err;
    }
    return sock;
err:
    return -1;

}

int tcp_client_send(struct socket *sock, const char *buf, const size_t length,unsigned long flags) {
    struct msghdr msg;
    struct kvec vec;
    int len, written = 0, left = length;
    mm_segment_t oldmm;

    msg.msg_name    = 0;
    msg.msg_namelen = 0;

    msg.msg_control = NULL;
    msg.msg_controllen = 0;
    msg.msg_flags   = flags;

    //oldmm = get_fs();
    //set_fs(KERNEL_DS);
repeat_send:

    vec.iov_len = left;
    vec.iov_base = (char *)buf + written;

    len = kernel_sendmsg(sock, &msg, &vec, 1, left);
    if(len > 0)
    {
        written += len;
        left -= len;
        if(left>0)
            goto repeat_send;
    }
    //set_fs(oldmm);
    return written ? written:len;
}
int tcp_client_receive(struct socket *sock, char *str,int max_size,unsigned long flags) {
    struct msghdr msg;
    struct kvec vec;
    int len;
    mm_segment_t oldmm;
    msg.msg_name    = 0;
    msg.msg_namelen = 0;

    msg.msg_control = NULL;
    msg.msg_controllen = 0;
    msg.msg_flags   = flags;

    vec.iov_len = max_size;
    vec.iov_base = str;
read_again:
    len = kernel_recvmsg(sock, &msg, &vec, 1, max_size, flags);

    return len;
}


