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

注释不能太多，也不能太少，主要添加在类和函数上。好的命名可以替代明确或简单的类和函数。

> 注意

注释 = 做什么，为什么，怎么做，怎么用，对一些边界条件，特殊情况进行说明，以及对函数输入，输出，异常进行说明。 写好注释很关键，可以通过以下两个方式去练习:

1. 学习javadoc
2. 阅读jdk源码

> 关于注释使用英文还是中文

个人觉得要看团队的规范，毕竟代码是提供给别人看的，如果个人或团队的英文水平较弱，使用中文也是一个提高效率不错的选择（虽然左耳朵老师建议使用英文注释）。

如下是上述代码的注释样例：

```java
/**
 * Id Generator that is used to generate random IDs.
 *
 * <p>
 * The IDs generated by this class are not absolutely unique,
 * but the probability of duplication is very low.
 */
class RandomIdGenerator implements LogTraceIdGenerator {

    /**
     * Generate the random ID. The IDs may be duplicated only in extreme situation.
     *
     * @return a random ID
     */
    @Override
    public String generate() {
        String substrOfHostName = getLastfieldOfHostName();
        long currentTimeMillis = System.currentTimeMillis();
        String randomString = generateRandomAlphameric(8);
        String id = String.format("%s-%d-%s",
                substrOfHostName, currentTimeMillis, randomString);
        return id;
    }

    /**
     * Get the local hostname and extract the last field of the name string splitted by delimiter '.'.
     *
     * @return the last field of hostname. Returns null if hostname is not obtained.
     */
    private String getLastfieldOfHostName() {
        String substrOfHostName = null;
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            substrOfHostName = getLastSubstrSplittedByDot(hostName);
        } catch (UnknownHostException e) {
            log.warn("Failed to get the host name.", e);
        }
        return substrOfHostName;
    }

    /**
     * Get the last field of {@code hostname} splitted by delemiter '.'.
     *
     * @param hostname should not be null
     * @return the last field of {@code hostname}. Returns empty string if {@code hostname} is empty string.
     */
    @VisibleForTesting
    protected String getLastSubstrSplittedByDot(String hostname) {
        String[] tokens = hostname.split("\\.");
        String substrOfHostName = tokens[tokens.length - 1];
        return substrOfHostName;
    }

    /**
     * Generate random string which
     * only contains digits, uppercase letters and lowercase letters.
     *
     * @param length should not be less than 0
     * @return the random string. Returns empty string if {@code length} is 0
     */
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

### 异常处理

对于上述单元测试无法通过，因为我们没有考虑异常情况：

1. 对于generate()函数，如果本机hostname获取失败，函数返回说明？
2. 对于getLastfieldOfHostName()函数，是否应该将UnknownHostException异常catch住并打印error日志？还是将异常继续抛出？或是将异常转化后再抛出？
3. 对于getLastSubstrSplittedByDot和generateRandomAlphameric输入参数为NULL或空字符串或length<0，函数该如何返回？ 

#### generate函数

ID由三个部分构成: 本机hostname, 时间戳和随机数。获取hostname可能获取失败。如果对于业务能够接受`null-16723733647-83Ab3uK6`这种数据则可以不做处理，但更推荐将异常告知调用者（因为该情况是不希望发生，并且发生是不正常的）。所以，这里推荐抛出受检查异常。

```java
public String generate() throws IdGenerationFailureException {
    String substrOfHostName = getLastFieldOfHostName();
    if (substrOfHostName == null || substrOfHostName.isEmpty()) {
        throw new IdGenerationFailureException("host name is empty.");
    }
    long currentTimeMillis = System.currentTimeMillis();
    String randomString = generateRandomAlphameric(8);
    String id = String.format("%s-%d-%s",
            substrOfHostName, currentTimeMillis, randomString);
    return id;
}
```

#### getLastfieldOfHostName 

对于getLastfieldOfHostName()函数，是否应该将异常在函数内部吞掉并打印日志，还是将异常继续往上抛出？如果往上继续抛出，需要将异常转化嘛？

> 返回NULL还是异常

对于函数返回NULL还是异常，要看获取不到数据是不是正常行为，如果获取主机hostname失败之后会影响后续逻辑的处理，并不是程序期望的，所以是一种异常行为。这里最好是抛出异常，而不是返回NULL值。但比如query,select等函数,如果不存在数据通常也是一种正常行为则返回空集合也是可以的。

> 异常是否需要转化

关于是否需要将异常转化，要异常是否有业务相关。UnknownHostException表示主机hostname获取失败是业务相关的，所以将异常抛出即可，不需要进行异常转化。

> 代码重构

根据上述分析，getLastfieldOfHostName需要将异常原封不动抛出，此时generate()需要捕获该异常，在generate函数中该如何处理该异常呢？根据上述分析: 

1. generate需要通知调用者异常;
2. UnknownHostException跟generate业务无相关，需要将异常转化后抛出;

```java
public String generate() throws IdGenerationFailureException {
    String substrOfHostName = null;
    try {
        substrOfHostName = getLastFieldOfHostName();
    } catch (UnknownHostException e) {
        throw new IdGenerationFailureException("host name is empty.");
    }
    long currentTimeMillis = System.currentTimeMillis();
    String randomString = generateRandomAlphameric(8);
    String id = String.format("%s-%d-%s", 
            substrOfHostName, currentTimeMillis, randomString);
    return id;
}
```

#### getLastSubstrSplittedByDot

对于getLastSubstrSplittedByDot()函数，如果hostname为NULL或者空字符串，该函数应该返回什么呢？

> 如果上层做了参数校验，下层需要再写一遍嘛？

理论上，参数传递的正确性应该有程序员来保证，不需要再做NULL或者空字符串的判断或者特殊处理。调用者本不应该传递NULL或者空字符串。

我认为，对于private私有函数，只在类内部调用，不要传递NULL值或者空字符串即可。如果是public函数，无法掌握会被谁调用以及如何调用。为了代码的健壮性，最好在public函数中做防御操作。

```java
@VisibleForTesting
protected String getLastSubstrSplittedByDot(String hostName) {
    if (hostName == null || hostName.isEmpty()) {
        throw IllegalArgumentException("..."); //运行时异常
    }
    String[] tokens = hostName.split("\\.");
    String substrOfHostName = tokens[tokens.length - 1];
    return substrOfHostName;
}
```

#### generateRandomAlphameric

对于generateRandomAlphameric函数，如果length<0或者length=0，函数应该返回什么？

> length < 0的情况

生成长度为负数的随机字符串是不符合常规逻辑的，是一种异常行为，所以length<0应该抛出异常。

> length = 0的情况

length=0的情况，需要根据业务来决定，可以将其视为一种异常行为，也可以直接返回空字符串。最关键的是需要在注释上指明length=0会返回什么样的数据。

### 重构后的代码

```java
/**
 * Id Generator that is used to generate random IDs.
 *
 * <p>
 * The IDs generated by this class are not absolutely unique,
 * but the probability of duplication is very low.
 */
public class RandomIdGenerator implements IdGenerator {

    /**
     * Generate the random ID. The IDs may be duplicated only in extreme situation.
     *
     * @return an random ID
     */
    @Override
    public String generate() throws IdGenerationFailureException {
        String substrOfHostName = null;
        try {
            substrOfHostName = getLastFieldOfHostName();
        } catch (UnknownHostException e) {
            throw new IdGenerationFailureException("...", e);
        }
        long currentTimeMillis = System.currentTimeMillis();
        String randomString = generateRandomAlphameric(8);
        return String.format("%s-%d-%s", substrOfHostName, currentTimeMillis, randomString);
    }

    /**
     * Get the local hostname and
     * extract the last field of the name string splitted by delimiter '.'.
     *
     * @return the last field of hostname. Returns null if hostname is not obtained.
     */
    private String getLastFieldOfHostName() throws UnknownHostException {
        String substrOfHostName = null;
        String hostName = InetAddress.getLocalHost().getHostName();
        if (StringUtils.isBlank(hostName)) {
            throw new UnknownHostException("...");
        }
        substrOfHostName = getLastSubstrSplittedByDot(hostName);
        return substrOfHostName;
    }

    /**
     * Get the last field of {@hostName} splitted by delemiter '.'.
     *
     * @param hostName should not be null
     * @return the last field of {@hostName}. Returns empty string if {@hostName} is empty string.
     */
    @VisibleForTesting
    protected String getLastSubstrSplittedByDot(String hostName) {
        if (StringUtils.isBlank(hostName)) {
            throw new IllegalArgumentException("...");
        }
        String[] tokens = hostName.split("\\.");
        return tokens[tokens.length - 1];
    }

    /**
     * Generate random string which
     * only contains digits, uppercase letters and lowercase letters.
     *
     * @param length should not be less than 0
     * @return the random string. Returns empty string if {@length} is 0
     */
    @VisibleForTesting
    protected String generateRandomAlphameric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("...");
        }
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

@Test
public void testGenerateRandomAlphameric_lengthEqualsOrLessThanZero() {
    RandomIdGenerator idGenerator = new RandomIdGenerator();
    String actualRandomString = idGenerator.generateRandomAlphameric(0);
    Assert.assertEquals("", actualRandomString);
    actualRandomString = idGenerator.generateRandomAlphameric(-1);
    Assert.assertNull(actualRandomString);
}
```

## 补充

### LinkedIn‘s Tips for Highly Effective Code Review

- Do I Understand the “Why”?
  在提交pr的同时需要描述本次修改的“动机”，有助于提高代码文档质量。

- Am I Giving Positive Feedback?
  当reviewer看到优秀代码需要给出正反馈。

- Is My Code Review Comment Explained Well?
  comment需要简洁易懂，比如"reduces duplication", "improves coverage"等等

- Do I Appreciate the Submitter’s Effort?
  每一次pr都需要被感谢，不管结果如何，使用谢谢

- Would This Review Comment Be Useful to Me?
  减少不必要的comment，比如代码格式有问题，开发者需要将CR意见当成有用的工具

- Is the “Testing Done” Section Thorough Enough?
  每一次变更都需要通过单元/接口测试

- Am I Too Pedantic in My Review?
  养成CR习惯，不要当成一种负担。养成一种心态：别人要CR我的代码，至少我的代码在自己CR下能够满意。

### 如何发现代码质量问题——常规checklist

- 目录设置是否合理，模块划分是否清晰，代码结构是否满足“高内聚，松耦合”
- 是否遵循经典的设计原则和设计思想（SOLID，DRY，KISS，YAGNI，LOD等）
- 设计模式是否应用得当？是否有过度设计？
- 代码是否容易扩展？如果要添加新功能，是否容易实现？
- 代码是否可以复用？是否可以复用已有的项目代码或者类库？是否有重复造轮子？
- 代码是否容易测试？单元测试是否全面覆盖各种正常和异常的情况？
- 代码是否易读？是否符合编码规范（比如命名和注释是否恰当、代码风格是否一致等）？

### 如何发现代码质量问题——业务需求checklist

- 代码是否实现了预期的业务需求？
- 逻辑是否正确？是否处理了各种异常情况？
- 日志打印是否得当？是否方便debug排查问题？
- 接口是否易用？是否支持幂等、事务等？
- 代码是否存在并发问题？是否线程安全？
- 性能是否有优化空间，比如，SQL、算法是否可以优化？
- 是否有安全漏洞？比如，输入输出校验是否全面？