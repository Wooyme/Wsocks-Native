char CONNECT = 0;
char CONNECT_SUCCESS = 1;
char EXCEPTION = 2;
char RAW = 3;
char HTTP = 4;
char KCP = 5;
char DNS = 6;
char HEART = 7;
char FILE_REQ = 8;
char FILE_RES = 9;
struct IUPDATE{
   char key[16];
   char ip[4];
   int port;
} __attribute__((packed));
struct IONLINE{
    char head[6];
    char version;
    char key[16];
} __attribute__((packed));
struct ICONNECT {
    char flag;
    char uuid[36];
    char host[256];
    int port;
} __attribute__((packed));

struct ICONNECT_SUCCESS {
    char flag;
    char uuid[36];
} __attribute__((packed));

struct IDNSQUERY {
    char flag;
    char uuid[36];
    char host[256];
} __attribute__((packed));

struct IEXCEPTION {
    char flag;
    char uuid[36];
    char code;
    char msg[127];
} __attribute__((packed));

struct IRAW {
    char flag;
    char uuid[36];
    char data[1200];
    int size;
} __attribute__((packed));

struct IDNS{
    char flag;
    char host[1024];
} __attribute__((packed));

struct IDNS_SUCCESS{
    char flag;
    int size;
    char host[1024];
    u32 ip;
} __attribute__((packed));

struct IFILE_REQ{
    char flag;
    char filename[512];
    long size;
    long offset;
} __attribute__((packed));

struct IFILE_RES{
    char flag;
    char filename[512];
    long size;
    long offset;
    char buf[1024];
} __attribute__((packed));
