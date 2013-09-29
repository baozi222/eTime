package wf.utils;

import java.io.IOException;
import java.net.Socket;

import android.util.Log;

public class InnerSocketBuilder {

	private String proxyHost = "127.0.0.1";
	private int proxyPort = 1053;

	private Socket innerSocket = null;

	private boolean isConnected = false;

	private final String TAG = "CMWRAP->InnerSocketBuilder";

	/**
	 * �������ɴ����������Ŀ�������������
	 * 
	 * @param proxyHost
	 *            �����������ַ
	 * @param proxyPort
	 *            ����������˿�
	 * @param target
	 *            Ŀ�������
	 */
	public InnerSocketBuilder(String proxyHost, int proxyPort, String target) {
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;

		connect();
	}

	private void connect() {

		// starTime = System.currentTimeMillis();
		Log.v(TAG, "����ͨ��");

		try {
			innerSocket = new Socket(proxyHost, proxyPort);
//			innerSocket.setKeepAlive(true);
			innerSocket.setTcpNoDelay(true);
			innerSocket.setSoTimeout(60 * 1000);
			isConnected = true;

		} catch (IOException e) {
			Log.e(TAG, "�������ʧ�ܣ�" + e.getLocalizedMessage());
		}
	}

	public Socket getSocket() {
		return innerSocket;
	}

	public boolean isConnected() {
		return this.isConnected;
	}

}
