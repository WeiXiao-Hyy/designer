## 引言

本文为《设计模式之美》的第34-38章的学习笔记，主要从一个IdGenerator类出发，一步一步将代码优化为可读性，可扩展性，可测试性优秀的代码。
主要记录优化过程以及个人思考。

相关源码可以从[https://github.com/WeiXiao-Hyy/design-patterns](https://github.com/WeiXiao-Hyy/design-patterns)获取，欢迎Star！

## 需求

在微服务开发中生成唯一请求ID的功能很常见，如下代码即是一个简单版本的ID生成器。整个ID由三个部分组成:

1. 本机名的最后一个字段
2. 当前的时间戳，精确到毫秒
3. 8位的随机字符串，包含大小写字母和数字

尽管该版本生成的ID并不是唯一的，有重复的可能，但是事实上重复的可能性的概率非常低。

```java

@Slf4j
public class IdGenerator {
    public static String generate() {
        String id = "";
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String[] tokens = hostName.split("\\.");
            if (tokens.length > 0) {
                hostName = tokens[tokens.length - 1];
            }
            char[] randomChars = new char[8];
            int count = 0;
            Random random = new Random();
            while (count < 8) {
                int randomAscii = random.nextInt(122);
                if (randomAscii >= 48 && randomAscii <= 57) {
                    randomChars[count] = (char) ('0' + (randomAscii - 48));
                    count++;
                } else if (randomAscii >= 65 && randomAscii <= 90) {
                    randomChars[count] = (char) ('A' + (randomAscii - 65));
                    count++;
                } else if (randomAscii >= 97 && randomAscii <= 122) {
                    randomChars[count] = (char) ('a' + (randomAscii - 97));
                    count++;
                }
            }
            id = String.format("%s-%d-%s", hostName, System.currentTimeMillis(), new String(randomChars));
        } catch (UnknownHostException e) {
            log.warn("Failed to get the host name.", e);
        }
        return id;
    }
}
```

## 优化步骤

从可读性，可测试性，编写完善的单元测试，所有重构完成之后添加注释四个步骤进行优化。

### 可读性

从基于接口而非实现编程的角度，其主要的目的是为了方便后续灵活地替换实现类。比如未来可能会有以下场景：

1. 需要生成微服务调用链请求唯一ID;
2. Auth2.0中的clientId,clientSecret生成;
3. 用户订单ID;
4. 等等

基于以上场景考虑有如下三个接口定义方式

|     |        接口         |           实现类            |
|-----|:-----------------:|:------------------------:|
| 命名一 |    IdGenerator    |   LogTraceIdGenerator    |
| 命名二 | LogTraceGenerator | HostNameMillsIdGenerator |
| 命名三 | LogTraceGenerator |    RandomIdGenerator     |

> 命名一

接口实现类设计为`LogTraceIdGenerator`, 如果未来存在用户(`UserIdGenerator`)，订单(`OrderIdGenerator`)等ID生成器,其实现类不能进行替换，所以让这三个类去实现IdGenerator接口，实际上没有意义。

> 命名二

接口为`LogTraceGenerator`没有问题，但是`HostNameMillsIdGenerator`暴露了太多的实现细节，只要代码稍微有所改动，就可能需要改动命名了。

> 命名三

对于命名三，生成的ID是一个随机ID，不是递增有序的，命名为RandomIdGenerator是比较合理的，即使内部生成算法有所改动，不需要改动命名。

> 最终方案

抽象出两个接口，一个是`IdGenerator`, 一个是`LogTraceIdGenerator`, `LogTraceIdGenerator`继承`IdGenerator`, 实现类实现接口`LogTraceIdGenerator`, 命名为`RandomIdGenerator`, 这样实现类可以复用到多个业务模块，比如用户，订单，OAuth等等。


基于以上优化得到如下代码:

```java
public interface IdGenerator {
    String generate();
}
public interface LogTraceIdGenerator extends IdGenerator {
}
@Slf4j
public class RandomIdGenerator implements LogTraceIdGenerator {
    @Override
    public String generate() {
        String substrOfHostName = getLastfieldOfHostName();
        long currentTimeMillis = System.currentTimeMillis();
        String randomString = generateRandomAlphameric(8);
        String id = String.format("%s-%d-%s", 
                substrOfHostName, currentTimeMillis, randomString);
        return id;
    }
    private String getLastfieldOfHostName() {
        String substrOfHostName = null;
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String[] tokens = hostName.split("\\.");
            substrOfHostName = tokens[tokens.length - 1];
            return substrOfHostName;
        } catch (UnknownHostException e) {
            log.warn("Failed to get the host name.", e);
        }
        return substrOfHostName;
    }
    private String generateRandomAlphameric(int length) {
        char[] randomChars = new char[length];
        int count = 0;
        Random random = new Random();
        while (count < length) {
            int maxAscii = 'z';
            int randomAscii = random.nextInt(maxAscii);
            boolean isDigit= randomAscii >= '0' && randomAscii <= '9';
            boolean isUppercase= randomAscii >= 'A' && randomAscii <= 'Z';
            boolean isLowercase= randomAscii >= 'a' && randomAscii <= 'z';
            if (isDigit|| isUppercase || isLowercase) {
                randomChars[count] = (char) (randomAscii);
                ++count;
            }
        }
        return new String(randomChars);
    }
}
```

### 可测试性

- generate函数为静态函数，不好写测试代码(除非用PowerMock);
- generate函数依赖时间函数、随机函数，机器的hostname所以可测试性不好;

> 将依赖环境或其他的函数剥离出来，单独测试其他部分 

将`getLastfieldOfHostName`分为`hostname`部分和`getLastSubstrSplittedByDot`单独测试`getLastSubstrSplittedByDot`即可。

> 将不好测试的private函数可以转化为protected+@VisibleForTesting

- protected的作用：可以直接在单元测试中通过对象来调用两个函数进行测试。
- @VisibleForTesting: 只起到标识作用，只是为了测试。

基于以上优化, 得到下述代码:

```java
public class RandomIdGenerator implements LogTraceIdGenerator {
    
    @Override
    public String generate() {
        String substrOfHostName = getLastfieldOfHostName();
        long currentTimeMillis = System.currentTimeMillis();
        String randomString = generateRandomAlphameric(8);
        String id = String.format("%s-%d-%s",
                substrOfHostName, currentTimeMillis, randomString);
        return id;
    }
    
    private String getLastfieldOfHostName() {
        String substrOfHostName = null;
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            substrOfHostName = getLastSubstrSplittedByDot(hostName);
        } catch (UnknownHostException e) {
            logger.warn("Failed to get the host name.", e);
        }
        return substrOfHostName;
    }
    
    @VisibleForTesting
    protected String getLastSubstrSplittedByDot(String hostName) {
        String[] tokens = hostName.split("\\.");
        String substrOfHostName = tokens[tokens.length - 1];
        return substrOfHostName;
    }
    
    @VisibleForTesting
    protected String generateRandomAlphameric(int length) {
        char[] randomChars = new char[length];
        int count = 0;
        Random random = new Random();
        while (count < length) {
            int maxAscii = 'z';
            int randomAscii = random.nextInt(maxAscii);
            boolean isDigit = randomAscii >= '0' && randomAscii <= '9';
            boolean isUppercase = randomAscii >= 'A' && randomAscii <= 'Z';
            boolean isLowercase = randomAscii >= 'a' && randomAscii <= 'z';
            if (isDigit || isUppercase || isLowercase) {
                randomChars[count] = (char) (randomAscii);
                ++count;
            }
        }
        return new String(randomChars);
    }
}
```

### 完善单元测试

基于上述重构，目前需要测试的函数如下:

```java
public String generate();

private String getLastfieldOfHostName();

@VisibleForTesting
protected String getLastSubstrSplittedByDot(String hostName);

@VisibleForTesting
protected String generateRandomAlphameric(int length);
```

对于后两个函数逻辑较为复杂, 是我们测试的重点, 单元测试代码如下:

1. 函数命名 testgetLastSubstrSplittedByDot_nullOrEmpty(): 团队统一即可,较推荐该种写法;
2. 注意各种边界条件, 字符串可能为null或"";
3. 有时还需要测试函数的执行次数,而不仅仅是返回结果的某个属性;

对于generate()函数，是唯一暴露给外部使用的public方法，其依赖主机名称、随机函数、时间函数，该如何编写测试函数呢？

> 注意

写单元测试的时候，测试对象是函数定义的功能，而非具体的实现逻辑。这样才能做到，即使函数的实现逻辑改变了，单元测试用例仍然可以工作。

1. generator功能定义为"生成一个随机唯一ID"，那么需要测试多次调用generate生成的ID是否唯一;
2. generator功能定义为"只包含数字、大小写字母和中划线的唯一ID"，那么不仅需要测试ID的唯一性，还需要测试ID的组成是否符合预期;
3. generator功能定义为"生成唯一ID，格式为{hostname}-{时间戳}-{8位随机数字}"，那么不仅需要测试ID的唯一性，还需要测试ID的组成是否符合预期;

对于`getLastfieldOfHostName()`实际上这个函数不容易测试，因为它调用了一个静态函数，并且这个静态函数依赖运行环境，但是这个函数的实现非常简单, 所以我认为不需要为其单独写单元测试。

基于以上分析，写出下述的单元测试代码，同时也观察到如果传入的字符串为null或"",`testGetLastSubstrSplittedByDot`函数会抛出异常，也验证了写单元测试可以帮助我们review自己的代码，同时提高代码的健壮性。

```java
@Test
public void testGetLastSubstrSplittedByDot() {
    RandomIdGenerator idGenerator = new RandomIdGenerator();
    String actualSubstr = idGenerator.getLastSubstrSplittedByDot("field1.field2.field3");
    Assert.assertEquals("field3", actualSubstr);
    actualSubstr = idGenerator.getLastSubstrSplittedByDot("field1");
    Assert.assertEquals("field1", actualSubstr);
    actualSubstr = idGenerator.getLastSubstrSplittedByDot("field1#field2#field3");
    Assert.assertEquals("field1#field2#field3", actualSubstr);
}

// 此单元测试会失败，因为我们在代码中没有处理hostName为null或空字符串的情况
@Test
public void testGetLastSubstrSplittedByDot_nullOrEmpty() {
    RandomIdGenerator idGenerator = new RandomIdGenerator();
    String actualSubstr = idGenerator.getLastSubstrSplittedByDot(null);
    Assert.assertNull(actualSubstr);
    actualSubstr = idGenerator.getLastSubstrSplittedByDot("");
    Assert.assertEquals("", actualSubstr);
}

@Test
public void testGenerateRandomAlphameric() {
    RandomIdGenerator idGenerator = new RandomIdGenerator();
    String actualRandomString = idGenerator.generateRandomAlphameric(6);
    Assert.assertNotNull(actualRandomString);
    Assert.assertEquals(6, actualRandomString.length());
    for (char c : actualRandomString.toCharArray()) {
        Assert.assertTrue(('0' <= c && c <= '9') || ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z'));
    }
}

// 此单元测试会失败，因为我们在代码中没有处理length<=0的情况
@Test
public void testGenerateRandomAlphameric_lengthEqualsOrLessThanZero() {
    RandomIdGenerator idGenerator = new RandomIdGenerator();
    String actualRandomString = idGenerator.generateRandomAlphameric(0);
    Assert.assertEquals("", actualRandomString);
    actualRandomString = idGenerator.generateRandomAlphameric(-1);
    Assert.assertNull(actualRandomString);
}
```

### 添加注释

