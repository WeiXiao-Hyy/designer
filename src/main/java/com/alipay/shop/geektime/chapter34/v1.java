package com.alipay.shop.geektime.chapter34;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

/**
 * @author hyy
 * @Description
 * @create 2024-04-09 22:51
 */
@Slf4j
public class v1 {
    interface IdGenerator {
        String generate();
    }

    interface LogTraceIdGenerator extends IdGenerator {

    }

    static class RandomIdGenerator implements LogTraceIdGenerator {

        @Override
        public String generate() {
            String substrOfHostName = getLastFiledOfHostName();
            long currentTimeMillis = System.currentTimeMillis();
            String randomString = generateRandomAlphameric(8);
            String id = String.format("%s-%d-%s", substrOfHostName, currentTimeMillis, randomString);
            return id;
        }

        private String getLastFiledOfHostName() {
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
         * Get the last field of {@code hostname} separated by delimiter '.'.
         *
         * @param hostname should not be null
         * @return the last field of {@code hostname}. Return empty string if {@code hostname}
         * is empty string.
         */
        @VisibleForTesting
        protected String getLastSubstrSplittedByDot(String hostname) {
            String[] tokens = hostname.split("\\.");
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
}


