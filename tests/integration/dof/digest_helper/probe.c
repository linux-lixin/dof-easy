#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
int main(int argc,char **argv){
 if(argc<4)return 2;void *lib=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);if(!lib)return 3;
 int (*version)(void)=dlsym(lib,"dof_admin_digest_version");
 int (*digest)(int,uint32_t,unsigned char*)=dlsym(lib,"dof_admin_sha256_fd");
 if(!version||!digest||version()!=1)return 4;
 int fd=open(argv[2],O_RDONLY|O_NONBLOCK|O_NOFOLLOW);if(fd<0)return 5;
 if(argc>4&&!strcmp(argv[4],"advance")){unsigned char b;if(read(fd,&b,1)!=1)return 6;}
 unsigned char bytes[32];int result=digest(fd,(uint32_t)strtoul(argv[3],0,10),argc>4&&!strcmp(argv[4],"null")?NULL:bytes);
 close(fd);if(result){puts("REJECTED");return 0;}for(int i=0;i<32;i++)printf("%02x",bytes[i]);puts("");return 0;
}
