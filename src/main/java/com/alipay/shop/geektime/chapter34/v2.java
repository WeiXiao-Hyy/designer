package com.alipay.shop.geektime.chapter34;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author hyy
 * @Description
 * @create 2024-04-11 10:52
 */
@Slf4j
public class v2 {
    interface IdGenerator {
        String generate();
    }

    interface LogTraceIdGenerator extends v1.IdGenerator {

    }


    class RandomIdGenerator implements LogTraceIdGenerator {
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
                log.warn("Failed to get the host name.", e);
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
}