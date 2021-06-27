#include <linux/kernel.h>
//Types of DNS resource records :)

#define T_A 1 //Ipv4 address
#define T_NS 2 //Nameserver
#define T_CNAME 5 // canonical name
#define T_SOA 6 /* start of authority zone */
#define T_PTR 12 /* domain name pointer */
#define T_MX 15 //Mail server

//DNS header structure
struct DNS_HEADER
{
    unsigned short id; // identification number

    unsigned char rd :1; // recursion desired
    unsigned char tc :1; // truncated message
    unsigned char aa :1; // authoritive answer
    unsigned char opcode :4; // purpose of message
    unsigned char qr :1; // query/response flag

    unsigned char rcode :4; // response code
    unsigned char cd :1; // checking disabled
    unsigned char ad :1; // authenticated data
    unsigned char z :1; // its z! reserved
    unsigned char ra :1; // recursion available

    unsigned short q_count; // number of question entries
    unsigned short ans_count; // number of answer entries
    unsigned short auth_count; // number of authority entries
    unsigned short add_count; // number of resource entries
};

//Constant sized fields of query structure
struct QUESTION
{
    unsigned short qtype;
    unsigned short qclass;
};

//Constant sized fields of the resource record structure
#pragma pack(push, 1)
struct R_DATA
{
    unsigned short type;
    unsigned short _class;
    unsigned int ttl;
    unsigned short data_len;
};
#pragma pack(pop)

//Pointers to resource record contents
struct RES_RECORD
{
    unsigned char *name;
    struct R_DATA *resource;
    unsigned char *rdata;
};

//Structure of a Query
typedef struct
{
    unsigned char *name;
    struct QUESTION *ques;
} QUERY;
/*
 * This will convert www.google.com to 3www6google3com
 * got it :)
 * */
void ChangetoDnsNameFormat(unsigned char* dns,unsigned char* host)
{
    int lock = 0, i;
    strcat((char*)host,".");

    for(i = 0 ; i < strlen((char*)host) ; i++)
    {
        if(host[i]=='.')
        {
            *dns++ = i-lock;
            for(; lock<i; lock++)
            {
                *dns++=host[lock];
            }
            lock++; //or lock=i+1;
        }
    }
    *dns++='\0';
}


u_char* ReadName(unsigned char* reader,unsigned char* buffer,int* count)
{
    unsigned char *name;
    unsigned int p=0,jumped=0,offset;
    int i, j;

    *count = 1;
    name = (unsigned char*)kmalloc(256,GFP_KERNEL);

    name[0]='\0';

    //read the names in 3www6google3com format
    while(*reader!=0)
    {
        if(*reader>=192)
        {
            offset = (*reader)*256 + *(reader+1) - 49152; //49152 = 11000000 00000000 ;)
            reader = buffer + offset - 1;
            jumped = 1; //we have jumped to another location so counting wont go up!
        }
        else
        {
            name[p++]=*reader;
        }

        reader = reader+1;

        if(jumped==0)
        {
            *count = *count + 1; //if we havent jumped to another location then we can count up
        }
    }

    name[p]='\0'; //string complete
    if(jumped==1)
    {
        *count = *count + 1; //number of steps we actually moved forward in the packet
    }

    //now convert 3www6google3com0 to www.google.com
    for(i=0; i<(int)strlen((const char*)name); i++)
    {
        p=name[i];
        for(j=0; j<(int)p; j++)
        {
            name[i]=name[i+1];
            i=i+1;
        }
        name[i]='.';
    }
    name[i-1]='\0'; //remove the last dot
    return name;
}

void send_lookup(unsigned char *host,int query_type,void *context,void (*send)(void *,int,unsigned char *)) {
    printk("Host: %s\n",host);
    struct DNS_HEADER *dns = NULL;
    struct QUESTION *qinfo = NULL;
    unsigned char *buf = kmalloc(2048,GFP_KERNEL);
    dns = (struct DNS_HEADER *)buf;

    dns->id = (unsigned short) htons(1);
    dns->qr = 0; //This is a query
    dns->opcode = 0; //This is a standard query
    dns->aa = 0; //Not Authoritative
    dns->tc = 0; //This message is not truncated
    dns->rd = 1; //Recursion Desired
    dns->ra = 0; //Recursion not available! hey we dont have it (lol)
    dns->z = 0;
    dns->ad = 0;
    dns->cd = 0;
    dns->rcode = 0;
    dns->q_count = htons(1); //we have only 1 question
    dns->ans_count = 0;
    dns->auth_count = 0;
    dns->add_count = 0;
    unsigned char *qname = (unsigned char*)&buf[sizeof(struct DNS_HEADER)];

    ChangetoDnsNameFormat(qname, host);
    qinfo =(struct QUESTION*)&buf[sizeof(struct DNS_HEADER) + (strlen((const char*)qname) + 1)]; //fill it

    qinfo->qtype = htons( query_type ); //type of the query , A , MX , CNAME , NS etc
    qinfo->qclass = htons(1);
    send(context,sizeof(struct DNS_HEADER)+strlen(qname)+1+sizeof(struct QUESTION),buf);

}

int handle_lookup(unsigned char* buf,unsigned char* host) {
    struct sockaddr_in a;
    struct DNS_HEADER *dns = buf;
    struct RES_RECORD answers[20]; //the replies from the DNS server
    //move ahead of the dns header and the query field
    unsigned char *qname = &buf[sizeof(struct DNS_HEADER)];
    unsigned char *reader = &buf[sizeof(struct DNS_HEADER) + (strlen((const char*)qname)+1) + sizeof(struct QUESTION)];
    memcpy(host,qname,strlen(qname));
    int stop = 0;
    int i;
    if(dns->ans_count<1) {
        return 0;
    }
    for(i=0; i<dns->ans_count && i<20; i++) {
        struct RES_RECORD answer = answers[i];
        answer.name=ReadName(reader,buf,&stop);
        reader = reader + stop;

        answer.resource = (struct R_DATA*)(reader);
        reader = reader + sizeof(struct R_DATA);

        if(ntohs(answer.resource->type) == 1) //if its an ipv4 address
        {
            answer.rdata = (unsigned char*)kmalloc(ntohs(answer.resource->data_len),GFP_KERNEL);
            int j;
            for(j=0 ; j<ntohs(answer.resource->data_len) ; j++)
            {
                answer.rdata[j]=reader[j];
            }

            answer.rdata[ntohs(answer.resource->data_len)] = '\0';

            reader = reader + ntohs(answer.resource->data_len);
            //memcpy(host,answer.name,strlen(answer.name));
            long p;
            p=*(long*)answer.rdata;
	    kfree(answer.name);
            kfree(answer.rdata);
	    return p;
        }else{
	    answer.rdata = ReadName(reader,buf,&stop);
	    reader = reader + stop;
	    kfree(answer.rdata);
	}
        kfree(answer.name);
    }

    return 0;

}

