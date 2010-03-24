package dareka.processor;

import java.io.OutputStream;

/**
 * データ転送中にシステムから呼び出されるイベントリスナ。
 * {@link Resource#addTransferListener(TransferListener)}に登録して使う。
 *
 */
public interface TransferListener {
    /**
     * アウトバウンド側からリクエストヘッダが到着したらシステムが呼び出す。
     * 
     * @param responseHeader
     */
    void onResponseHeader(HttpResponseHeader responseHeader);

    /**
     * [nl] データの転送を始める前に呼び出す。
     * 
     * @param receiverOut 
     */
    void onTransferBegin(OutputStream receiverOut);

    /**
     * アウトバウンド側からインバウンド側にデータを転送するたびに
     * システムが呼び出す。
     * 
     * @param buf 転送するデータ。
     * @param length bufの中の有効なデータの長さ。
     */
    void onTransferring(byte[] buf, int length);

    /**
     * データの転送が終了したらシステムが呼び出す。
     * 
     * @param completed 最後まで転送できたらtrue。途中で中断した場合はfalse。
     */
    void onTransferEnd(boolean completed);

}
