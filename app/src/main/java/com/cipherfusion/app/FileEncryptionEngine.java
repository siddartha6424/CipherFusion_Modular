package com.cipherfusion.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class FileEncryptionEngine {
 private static final byte[] MAGIC="CFFILE01".getBytes(StandardCharsets.US_ASCII);
 private static final int TABLE_SIZE=256;
 private FileEncryptionEngine(){}
 public static String encrypt(InputStream in, OutputStream out, String key, String extension, long total, Progress p) throws IOException {
  byte[] kb=keyBytes(key); byte[] table=createTable(kb); int[] pos=new int[256]; for(int i=0;i<256;i++)pos[table[i]&255]=i;
  DataOutputStream d=new DataOutputStream(new BufferedOutputStream(out)); d.write(MAGIC); byte[] eb=extension.getBytes(StandardCharsets.UTF_8); d.writeShort(eb.length); d.write(eb); d.writeLong(total);
  BufferedInputStream bin=new BufferedInputStream(in,65536); byte[] buf=new byte[65536]; long done=0; StringBuilder hex=new StringBuilder(131072); int n;
  while((n=bin.read(buf))!=-1){ for(int i=0;i<n;i++){int v=(buf[i]&255)+(kb[(int)((done+i)%kb.length)]&255)&255; int pp=pos[v]; hex.append(Character.forDigit(pp>>>4,16)); hex.append(Character.forDigit(pp&15,16));} done+=n; if(hex.length()>1048576){d.write(hex.toString().getBytes(StandardCharsets.US_ASCII)); hex.setLength(0);} if(p!=null)p.onProgress(done,total); }
  if(hex.length()>0)d.write(hex.toString().getBytes(StandardCharsets.US_ASCII)); d.flush(); return extension;
 }
 public static Header readHeader(InputStream in) throws IOException { DataInputStream d=new DataInputStream(new BufferedInputStream(in)); byte[] m=new byte[8]; d.readFully(m); if(!java.util.Arrays.equals(m,MAGIC))throw new IOException("Not a CipherFusion file"); int len=d.readUnsignedShort(); if(len>4096)throw new IOException("Invalid header"); byte[] eb=new byte[len]; d.readFully(eb); long size=d.readLong(); return new Header(d,size,new String(eb,StandardCharsets.UTF_8)); }
 public static void decrypt(InputStream raw, OutputStream out, String key, long total, Progress p) throws IOException {
  Header h=readHeader(raw); DataInputStream d=h.stream; byte[] kb=keyBytes(key); byte[] table=createTable(kb); BufferedOutputStream bout=new BufferedOutputStream(out,65536); byte[] pair=new byte[2]; long done=0; int a;
  while((a=d.read())!=-1){ pair[0]=(byte)a; int b=d.read(); if(b==-1)throw new IOException("Truncated ciphertext"); pair[1]=(byte)b; int pos=(hex(pair[0])<<4)|hex(pair[1]); if(pos<0)throw new IOException("Invalid ciphertext"); int v=table[pos]&255; int original=(v-(kb[(int)(done%kb.length)]&255)+256)&255; bout.write(original); done++; if(p!=null)p.onProgress(done,h.originalSize); } bout.flush(); if(done!=h.originalSize)throw new IOException("Size mismatch");
 }
 private static int hex(byte b){int c=b&255; if(c>='0'&&c<='9')return c-'0'; if(c>='a'&&c<='f')return c-'a'+10; if(c>='A'&&c<='F')return c-'A'+10; return -1;}
 private static byte[] keyBytes(String k){if(k==null||k.trim().isEmpty())k="CIPHER"; return k.getBytes(StandardCharsets.UTF_8);}
 private static byte[] createTable(byte[] key){byte[] t=new byte[256]; for(int i=0;i<256;i++)t[i]=(byte)i; long seed=1469598103934665603L; for(byte b:key){seed^=b&255;seed*=1099511628211L;} Random r=new Random(seed); for(int i=255;i>0;i--){int j=r.nextInt(i+1);byte x=t[i];t[i]=t[j];t[j]=x;}return t;}
 public interface Progress{void onProgress(long done,long total);}
 public static final class Header{final DataInputStream stream;final long originalSize;final String extension;Header(DataInputStream s,long z,String e){stream=s;originalSize=z;extension=e;}}
}
