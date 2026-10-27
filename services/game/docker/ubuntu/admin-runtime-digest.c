/* SPDX-License-Identifier: MIT
 * Source-owned streaming SHA-256 for the fixed runtime verifier. No sockets,
 * subprocesses, paths, global mutable state or game data writes are exposed.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#if !defined(__i386__)
#error Build this helper for the pinned i386 game ABI.
#endif

struct digest { uint32_t h[8]; uint64_t length; unsigned used; unsigned char block[64]; };
static uint32_t rr(uint32_t x,unsigned n){return (x>>n)|(x<<(32-n));}
static void transform(struct digest *s,const unsigned char *p){
    static const uint32_t k[64]={
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    uint32_t w[64],a=s->h[0],b=s->h[1],c=s->h[2],d=s->h[3],e=s->h[4],f=s->h[5],g=s->h[6],h=s->h[7];
    for(unsigned i=0;i<16;i++)w[i]=((uint32_t)p[i*4]<<24)|((uint32_t)p[i*4+1]<<16)|((uint32_t)p[i*4+2]<<8)|p[i*4+3];
    for(unsigned i=16;i<64;i++)w[i]=w[i-16]+(rr(w[i-15],7)^rr(w[i-15],18)^(w[i-15]>>3))+w[i-7]+(rr(w[i-2],17)^rr(w[i-2],19)^(w[i-2]>>10));
    for(unsigned i=0;i<64;i++){
        uint32_t t1=h+(rr(e,6)^rr(e,11)^rr(e,25))+((e&f)^((~e)&g))+k[i]+w[i];
        uint32_t t2=(rr(a,2)^rr(a,13)^rr(a,22))+((a&b)^(a&c)^(b&c));
        h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2;
    }
    s->h[0]+=a;s->h[1]+=b;s->h[2]+=c;s->h[3]+=d;s->h[4]+=e;s->h[5]+=f;s->h[6]+=g;s->h[7]+=h;
}
static void update(struct digest *s,const unsigned char *p,size_t n){
    s->length+=n;
    while(n){size_t take=64-s->used;if(take>n)take=n;memcpy(s->block+s->used,p,take);s->used+=(unsigned)take;p+=take;n-=take;
        if(s->used==64){transform(s,s->block);s->used=0;}}
}
static void finish(struct digest *s,unsigned char out[32]){
    uint64_t bits=s->length*8;s->block[s->used++]=0x80;
    if(s->used>56){memset(s->block+s->used,0,64-s->used);transform(s,s->block);s->used=0;}
    memset(s->block+s->used,0,56-s->used);
    for(unsigned i=0;i<8;i++)s->block[63-i]=(unsigned char)(bits>>(i*8));
    transform(s,s->block);
    for(unsigned i=0;i<32;i++)out[i]=(unsigned char)(s->h[i/4]>>(24-8*(i%4)));
}
__attribute__((visibility("default"))) int dof_admin_digest_version(void){return 1;}
__attribute__((visibility("default"))) int dof_admin_sha256_fd(int fd,uint32_t expected,unsigned char out[32]){
    struct stat64 before,after;
    if(!out||expected>16777216u){errno=EINVAL;return -1;}
    if(fstat64(fd,&before)!=0)return -1;
    if(!S_ISREG(before.st_mode)||before.st_size!=(off64_t)expected||lseek64(fd,0,SEEK_CUR)!=0){errno=EINVAL;return -1;}
    struct digest s={{0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19},0,0,{0}};
    unsigned char buffer[32768];uint32_t total=0;
    for(;;){ssize_t n=read(fd,buffer,sizeof buffer);if(n<0){if(errno==EINTR)continue;return -1;}if(n==0)break;
        if((uint32_t)n>expected-total){errno=ESTALE;return -1;}total+=(uint32_t)n;update(&s,buffer,(size_t)n);}
    if(total!=expected||fstat64(fd,&after)!=0){errno=ESTALE;return -1;}
    if(before.st_dev!=after.st_dev||before.st_ino!=after.st_ino||before.st_size!=after.st_size||
       before.st_mtim.tv_sec!=after.st_mtim.tv_sec||before.st_mtim.tv_nsec!=after.st_mtim.tv_nsec||
       before.st_ctim.tv_sec!=after.st_ctim.tv_sec||before.st_ctim.tv_nsec!=after.st_ctim.tv_nsec){errno=ESTALE;return -1;}
    finish(&s,out);return 0;
}
