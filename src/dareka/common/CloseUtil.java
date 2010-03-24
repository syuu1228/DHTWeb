package dareka.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * リソースのclose()を呼ぶユーティリティクラス。
 * リソースリークを避けるため、close()が必要なリソースを確保したら
 * 必ずtry節の中で使用し、finally節でclose()する必要がある。
 * colse()はIOExceptionをthrowするので、finally節の中でも
 * try節を使う必要がありコーディングが冗長になるので、
 * 処理をこのクラスにまとめる。
 * 
 */
public class CloseUtil {
	public static Logger logger = LoggerFactory.getLogger(CloseUtil.class);
    private CloseUtil() {
        // avoid instantiation.
    }

    /**
     * リソースを例外の発生なしに閉じる。
     * 
     * @param resource 閉じるリソース。nullの場合は何もせずに成功扱い。
     * @return 成功したらtrue。何らかのエラーが発生したらfalse。
     */
    public static boolean close(Closeable resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            logger.debug(e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * リソースを例外の発生なしに閉じる。
     * SelectorはCloseableを実装しないのでオーバーロードで対応。
     * 
     * @param resource 閉じるリソース。nullの場合は何もせずに成功扱い。
     * @return 成功したらtrue。何らかのエラーが発生したらfalse。
     */
    public static boolean close(Selector resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            logger.debug(e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * リソースを例外の発生なしに閉じる。
     * SocketはJava SE 7までCloseableを実装しないので
     * 現時点ではオーバーロードで対応。
     * 
     * @param resource 閉じるリソース。nullの場合は何もせずに成功扱い。
     * @return 成功したらtrue。何らかのエラーが発生したらfalse。
     */
    public static boolean close(Socket resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            logger.debug(e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * リソースを例外の発生なしに閉じる。
     * ServerSocketはJava SE 7までCloseableを実装しないので
     * 現時点ではオーバーロードで対応。
     * 
     * @param resource 閉じるリソース。nullの場合は何もせずに成功扱い。
     * @return 成功したらtrue。何らかのエラーが発生したらfalse。
     */
    public static boolean close(ServerSocket resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            logger.debug(e.getMessage());
            return false;
        }

        return true;
    }

}
