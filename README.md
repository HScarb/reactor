## Reactor Java Implementation

依据 Doug Lea 的 [Scalable IO in Java](http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf) 基于 NIO 实现的 Reactor 模式的大小写转换服务器。模仿 Netty 实现主从 Reactor 模型。

原文地址：https://hscarb.github.io/java/20240827-reactor-java.html

## Getting started

### Compiling

```java
javac -encoding utf-8 com\cnscarb\reactor\bio\*.java
javac -encoding utf-8 com\cnscarb\reactor\reactor\*.java
javac -encoding utf-8 com\cnscarb\reactor\*.java
```

### Running

```java
java com.cnscarb.reactor.Main
```

### Testing

使用 telnet 命令进行测试，在 windows 下记得打开回显（ctrl+] -> set localecho -> Enter）

```bash
D:\>telnet 127.0.0.1 8080
reactor> Hello World!
HELLO WORLD!
reactor>

Connection to host lost.
D:\>
```
