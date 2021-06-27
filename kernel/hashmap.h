#include <linux/string.h>

#define NR_BUCKETS 1024

struct StrHashNode {
    char *key;
    void *value;
    struct StrHashNode *next;

};

struct StrHashTable {
    struct StrHashNode *buckets[NR_BUCKETS];
    void (*free_key)(char *);
    void (*free_value)(void*);
    unsigned int (*hash)(const char *key);
    int (*cmp)(const char *first,const char *second);
};
void *remove(struct StrHashTable *table,const char *key) {
    unsigned int bucket = table->hash(key)%NR_BUCKETS;
    struct StrHashNode *node;
    struct StrHashNode *prev = NULL;
    node = table->buckets[bucket];

    while(node) {
        if(table->cmp(key,node->key)==0) {
            if(prev==NULL) {
                table->buckets[bucket] = node->next;
                kfree(node->key);
                void *value = node->value;
                kfree(node);
                return value;
            } else {
                prev->next = node->next;
                kfree(node->key);
                void *value = node->value;
                kfree(node);
                return value;
            }
        }
        node = node->next;
        prev = node;
    }
    return NULL;
}
void *get(struct StrHashTable *table,const char *key)
{
    unsigned int bucket = table->hash(key)%NR_BUCKETS;
    struct StrHashNode *node;
    node = table->buckets[bucket];
    while(node) {
        if(table->cmp(key,node->key) == 0)
            return node->value;
        node = node->next;
    }
    return NULL;
}

void *_get(struct StrHashTable *table,const char *key)
{
    printk("key: %.*s\n",36,key);
    unsigned int bucket = table->hash(key)%NR_BUCKETS;
    printk("0\n");
    struct StrHashNode *node;
    node = table->buckets[bucket];
    printk("0.1\n");
    while(node) {
	printk("1\n");
        if(table->cmp(key,node->key) == 0)
            return node->value;
	printk("2\n");
        node = node->next;
        printk("3\n");
    }
    return NULL;
}
void boom(struct StrHashTable *table,void *context,int (*action)(void*,void*,void*)) {
    int i;
    struct StrHashNode *node;
    for(i=0; i<NR_BUCKETS; i++) {
        node = table->buckets[i];
        while(node) {
            action(context,node->key,node->value);
            struct StrHashNode *next = node->next;
	    kfree(node->key);
	    kfree(node);
	    node = next;
        }
    }
    kfree(table);
}
void foreach(struct StrHashTable *table,void *context,int (*action)(void*,void*,void*)) {
    int i;
    struct StrHashNode *node;
    
    struct StrHashNode *prev;
    for(i=0; i<NR_BUCKETS; i++) {
        node = table->buckets[i];
        prev = 0;
	while(node) {
            struct StrHashNode *next = node->next;
	    if(action(context,node->key,node->value)<0){
		if(prev==0){
		   *(&table->buckets[i]) = next;//node->next;
		}else{
		    prev->next = next;
		}
		kfree(node->key);
		kfree(node);
	    }else{
	    	prev = node;
	    }
	    node = next;
        }
    }
}
int insert(struct StrHashTable *table,char *key,void *value)
{
    unsigned int bucket = table->hash(key)%NR_BUCKETS;
    struct StrHashNode **tmp;
    struct StrHashNode *node ;

    tmp = &table->buckets[bucket];
    while(*tmp) {
        if(table->cmp(key,(*tmp)->key) == 0)
            break;
        tmp = &(*tmp)->next;
    }
    if(*tmp) {
        if(table->free_key != NULL)
            table->free_key((*tmp)->key);
        if(table->free_value != NULL)
            table->free_value((*tmp)->value);
        node = *tmp;
    } else {
        node = kmalloc(sizeof *node,GFP_KERNEL);
        if(node == NULL)
            return -1;
        node->next = NULL;
        *tmp = node;
    }
    node->key = key;
    node->value = value;

    return 0;
}
int uuidcmp(const char *uuid1,const char *uuid2) {
    int i;
    for(i=0; i<36; i++) {
        if(uuid1[i]!=uuid2[i]) {
            return -1;
        }
    }
    return 0;
}

int pointcmp(void *p1,void *p2) {
    return p1!=p2;
}

unsigned int uuidhash(const char *uuid){
    unsigned int hash = 0;
    int i;
    for(i=0; i<36; i++)
        hash = 31*hash + uuid[i];
    return hash;
}
unsigned int strhash(const char *str)
{
    unsigned int hash = 0;
    for(; *str; str++)
        hash = 31*hash + *str;
    return hash;
}

unsigned int pointhash(void *p) {
    char *str = &p;
    unsigned int hash = 0;
    int i;
    for(i=0; i<sizeof(void *); i++) {
        hash = 31*hash + str[i];
    }
    return hash;
}
