package wf.com;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Random;

import wf.utils.Base64;
import wf.utils.DomainValidator;
import wf.utils.InnerSocketBuilder;
import wf.utils.Utils;
import wf.com.AgencyService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * �����װ��һ��Dns��Ӧ
 * 
 * @author biaji
 * 
 */
class DnsResponse implements Serializable {

	private static final long serialVersionUID = -6693216674221293274L;

	private String request = null;
	private long timestamp = System.currentTimeMillis();;
	private int reqTimes = 0;
	private byte[] dnsResponse = null;

	public DnsResponse(String request) {
		this.request = request;
	}

	/**
	 * @return the dnsResponse
	 */
	public byte[] getDnsResponse() {
		this.reqTimes++;
		return dnsResponse;
	}

	/**
	 * @return IP string
	 */
	public String getIPString() {
		String ip = null;
		int i;

		if (dnsResponse == null) {
			return null;
		}

		i = dnsResponse.length - 4;

		if (i < 0) {
			return null;
		}

		ip = "" + (dnsResponse[i] & 0xFF); /* Unsigned byte to int */

		for (i++; i < dnsResponse.length; i++) {
			ip += "." + (dnsResponse[i] & 0xFF);
		}

		return ip;
	}

	/**
	 * @return the reqTimes
	 */
	public int getReqTimes() {
		return reqTimes;
	}

	public String getRequest() {
		return this.request;
	}

	/**
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * @param dnsResponse
	 *            the dnsResponse to set
	 */
	public void setDnsResponse(byte[] dnsResponse) {
		this.dnsResponse = dnsResponse;
	}
}

/**
 * ����ʵ����DNS����
 * 
 * @author biaji
 * 
 */
public class DNSServer implements WrapServer {

	public static byte[] int2byte(int res) {
		byte[] targets = new byte[4];

		targets[0] = (byte) (res & 0xff);// ���λ
		targets[1] = (byte) ((res >> 8) & 0xff);// �ε�λ
		targets[2] = (byte) ((res >> 16) & 0xff);// �θ�λ
		targets[3] = (byte) (res >>> 24);// ���λ,�޷������ơ�
		return targets;
	}

	private final String TAG = "Agency";
	private String homePath;
	private final String CACHE_PATH = "/cache";

	private final String CACHE_FILE = "/dnscache";

	private DatagramSocket srvSocket;

	private int srvPort = 0;
	private String name;
	protected String dnsHost;
	protected int dnsPort;
	protected Context context;
	private volatile int threadNum = 0;
	private final static int MAX_THREAD_NUM = 5;
	public HashSet<String> domains;

	final protected int DNS_PKG_HEADER_LEN = 12;
	final private int[] DNS_HEADERS = { 0, 0, 0x81, 0x80, 0, 0, 0, 0, 0, 0, 0,
			0 };
	final private int[] DNS_PAYLOAD = { 0xc0, 0x0c, 0x00, 0x01, 0x00, 0x01,
			0x00, 0x00, 0x00, 0x3c, 0x00, 0x04 };

	final private int IP_SECTION_LEN = 4;

	private boolean inService = false;

	private Hashtable<String, DnsResponse> dnsCache = new Hashtable<String, DnsResponse>();

	/**
	 * �ڽ��Զ��建��
	 * 
	 */
	private Hashtable<String, String> orgCache = new Hashtable<String, String>();

	private String target = "8.8.8.8:53";
	private static final String CANT_RESOLVE = "Error";

	public DNSServer(String name, String dnsHost, int dnsPort, Context context) {
		this.name = name;
		this.dnsHost = dnsHost;
		this.dnsPort = dnsPort;
		this.context = context;

		domains = new HashSet<String>();

		if (dnsHost != null && !dnsHost.equals(""))
			target = dnsHost + ":" + dnsPort;
	}

	/**
	 * �ڻ��������һ����������
	 * 
	 * @param questDomainName
	 *            ����
	 * @param answer
	 *            �������
	 */
	private synchronized void addToCache(String questDomainName, byte[] answer) {
		DnsResponse response = new DnsResponse(questDomainName);
		response.setDnsResponse(answer);
		dnsCache.put(questDomainName, response);
		saveCache();
	}

	@Override
	public void close() throws IOException {
		inService = false;
		if (srvSocket != null) {
			srvSocket.close();
			srvSocket = null;
		}
		saveCache();
		Log.i(TAG, "DNS����ر�");
	}

	/*
	 * Create a DNS response packet, which will send back to application.
	 * 
	 * @author yanghong
	 * 
	 * Reference to:
	 * 
	 * Mini Fake DNS server (Python)
	 * http://code.activestate.com/recipes/491264-mini-fake-dns-server/
	 * 
	 * DOMAIN NAMES - IMPLEMENTATION AND SPECIFICATION
	 * http://www.ietf.org/rfc/rfc1035.txt
	 */
	protected byte[] createDNSResponse(byte[] quest, byte[] ips) {
		byte[] response = null;
		int start = 0;

		response = new byte[128];

		for (int val : DNS_HEADERS) {
			response[start] = (byte) val;
			start++;
		}

		System.arraycopy(quest, 0, response, 0, 2); /* 0:2 */
		System.arraycopy(quest, 4, response, 4, 2); /* 4:6 -> 4:6 */
		System.arraycopy(quest, 4, response, 6, 2); /* 4:6 -> 7:9 */
		System.arraycopy(quest, DNS_PKG_HEADER_LEN, response, start,
				quest.length - DNS_PKG_HEADER_LEN); /* 12:~ -> 15:~ */
		start += quest.length - DNS_PKG_HEADER_LEN;

		for (int val : DNS_PAYLOAD) {
			response[start] = (byte) val;
			start++;
		}

		/* IP address in response */
		for (byte ip : ips) {
			response[start] = ip;
			start++;
		}

		byte[] result = new byte[start];
		System.arraycopy(response, 0, result, 0, start);
		Log.d(TAG, "DNS Response package size: " + start);

		return result;
	}

	/**
	 * ���ϼ�DNSͨ��TCPȡ�ý���
	 * 
	 * @param quest
	 *            ԭʼDNS����
	 * @return
	 */
	protected byte[] fetchAnswer(byte[] quest) {

		Socket innerSocket = new InnerSocketBuilder("8.8.4.4", 53, "8.8.4.4:53")
				.getSocket();
		DataInputStream in;
		DataOutputStream out;
		byte[] result = null;
		try {
			if (innerSocket != null && innerSocket.isConnected()) {
				// ����TCP DNS��
				int dnsqLength = quest.length;
				byte[] tcpdnsq = new byte[dnsqLength + 2];
				System.arraycopy(int2byte(dnsqLength), 0, tcpdnsq, 1, 1);
				System.arraycopy(quest, 0, tcpdnsq, 2, dnsqLength);

				// ת��DNS
				in = new DataInputStream(innerSocket.getInputStream());
				out = new DataOutputStream(innerSocket.getOutputStream());
				out.write(tcpdnsq);
				out.flush();

				ByteArrayOutputStream bout = new ByteArrayOutputStream();

				int b = -1;
				while ((b = in.read()) != -1) {
					bout.write(b);
				}
				byte[] tcpdnsr = bout.toByteArray();
				if (tcpdnsr != null && tcpdnsr.length > 2) {
					result = new byte[tcpdnsr.length - 2];
					System.arraycopy(tcpdnsr, 2, result, 0, tcpdnsr.length - 2);
				}
				innerSocket.close();
			}
		} catch (IOException e) {
			Log.e(TAG, "", e);
		}
		return result;
	}

	public byte[] fetchAnswerHTTP(byte[] quest) {
		byte[] result = null;
		String domain = getRequestDomain(quest);
		String ip = null;

		DomainValidator dv = DomainValidator.getInstance();

		/* Not support reverse domain name query */
		if (domain.endsWith("in-addr.arpa") || !dv.isValid(domain)) {
			return createDNSResponse(quest, parseIPString("127.0.0.1"));
			// return null;
		}

		ip = resolveDomainName(domain);

		if (ip == null) {
			Log.e(TAG, "Failed to resolve domain name: " + domain);
			return null;
		}

		if (ip.equals(CANT_RESOLVE)) {
			return null;
		}

		byte[] ips = parseIPString(ip);
		if (ips != null) {
			result = createDNSResponse(quest, ips);
		}

		return result;
	}

	/**
	 * ��ȡUDP DNS���������
	 * 
	 * @param request
	 *            dns udp��
	 * @return ���������
	 */
	protected String getRequestDomain(byte[] request) {
		String requestDomain = "";
		int reqLength = request.length;
		if (reqLength > 13) { // ��������
			byte[] question = new byte[reqLength - 12];
			System.arraycopy(request, 12, question, 0, reqLength - 12);
			requestDomain = parseDomain(question);
			if (requestDomain.length() > 1)
				requestDomain = requestDomain.substring(0,
						requestDomain.length() - 1);
		}
		return requestDomain;
	}

	@Override
	public int getServPort() {
		return this.srvPort;
	}

	public int init() {
		try {
			srvSocket = new DatagramSocket(0,
					InetAddress.getByName("127.0.0.1"));
			Log.e("srvSocket", srvSocket.getLocalPort()+"");
			inService = true;
			srvPort = srvSocket.getLocalPort();
			Log.e(TAG, this.name + "�����ڶ˿ڣ� " + srvPort);
		} catch (SocketException e) {
			Log.e(TAG, "DNSServer��ʼ�����󣬶˿ں�" + srvPort, e);
		} catch (UnknownHostException e) {
			Log.e(TAG, "DNSServer��ʼ�����󣬶˿ں�" + srvPort, e);
		}
		return srvPort;
	}

	@Override
	public boolean isClosed() {
		return srvSocket.isClosed();
	}

	public boolean isInService() {
		return inService;
	}

	/**
	 * �ɻ�������������������
	 */
	private void loadCache() {
		ObjectInputStream ois = null;
		File cache = new File(homePath + CACHE_PATH + CACHE_FILE);
		try {
			if (!cache.exists())
				return;
			ois = new ObjectInputStream(new FileInputStream(cache));
			dnsCache = (Hashtable<String, DnsResponse>) ois.readObject();
			ois.close();
			ois = null;

			Hashtable<String, DnsResponse> tmpCache = (Hashtable<String, DnsResponse>) dnsCache
					.clone();
			for (DnsResponse resp : dnsCache.values()) {
				// ��黺��ʱЧ(����)
				if ((System.currentTimeMillis() - resp.getTimestamp()) > 432000000L) {
					Log.d(TAG, "ɾ��" + resp.getRequest() + "��¼");
					tmpCache.remove(resp.getRequest());
				}
			}

			dnsCache = tmpCache;
			tmpCache = null;

		} catch (ClassCastException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} catch (ClassNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} finally {
			try {
				if (ois != null)
					ois.close();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * ��������
	 * 
	 * @param request
	 * @return
	 */
	private String parseDomain(byte[] request) {

		String result = "";
		int length = request.length;
		int partLength = request[0];
		if (partLength == 0)
			return result;
		try {
			byte[] left = new byte[length - partLength - 1];
			System.arraycopy(request, partLength + 1, left, 0, length
					- partLength - 1);
			result = new String(request, 1, partLength) + ".";
			result += parseDomain(left);
		} catch (Exception e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
		return result;
	}

	/*
	 * Parse IP string into byte, do validation.
	 * 
	 * @param ip IP string
	 * 
	 * @return IP in byte array
	 */
	protected byte[] parseIPString(String ip) {
		byte[] result = null;
		int value;
		int i = 0;
		String[] ips = null;

		ips = ip.split("\\.");

		Log.d(TAG, "Start parse ip string: " + ip + ", Sectons: " + ips.length);

		if (ips.length != IP_SECTION_LEN) {
			Log.e(TAG, "Malformed IP string number of sections is: "
					+ ips.length);
			return null;
		}

		result = new byte[IP_SECTION_LEN];

		for (String section : ips) {
			try {
				value = Integer.parseInt(section);

				/* 0.*.*.* and *.*.*.0 is invalid */
				if ((i == 0 || i == 3) && value == 0) {
					return null;
				}

				result[i] = (byte) value;
				i++;
			} catch (NumberFormatException e) {
				Log.e(TAG, "Malformed IP string section: " + section);
				return null;
			}
		}

		return result;
	}

	/*
	 * Resolve host name by access a DNSRelay running on GAE:
	 * 
	 * Example:
	 * 
	 * http://www.hosts.dotcloud.com/lookup.php?(domain name encoded)
	 * http://gaednsproxy.appspot.com/?d=(domain name encoded)
	 */
	private String resolveDomainName(String domain) {
		String ip = null;

		InputStream is;

		String encode_host = URLEncoder.encode(Base64.encodeBytes(Base64
				.encodeBytesToBytes(domain.getBytes())));

		String url = "http://weiboagency3.sinaapp.com/dns.php?dn=" + encode_host;

		Random random = new Random(System.currentTimeMillis());
		int n = random.nextInt(2);
		if (n == 1)
			//url = "http://gaednsproxy1.appspot.com:" + dnsPort + "/?d=" + encode_host;
			url = "http://weiboagency3.sinaapp.com/dns.php?dn=" + encode_host;

		Log.d(TAG, "DNS Relay URL: " + url);
		Log.d(TAG, "DNS Port" + dnsPort);
		Log.e("Before", "connection in resolveDomainName");
		try {
			URL aURL = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) aURL.openConnection();
			conn.setConnectTimeout(2000);
			conn.setConnectTimeout(5000);
			conn.connect();
			is = conn.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			ip = br.readLine();
			Log.e("DNSSERVER ip:", ip);
		} catch (SocketException e) {
			Log.e(TAG, "Failed to request URI: " + url, e);
		} catch (IOException e) {
			Log.e(TAG, "Failed to request URI: " + url, e);
		} catch (NullPointerException e) {
			Log.e(TAG, "Failed to request URI: " + url, e);
		}

		return ip;
	}

	/*
	 * Implement with http based DNS.
	 */

	@Override
	public void run() {

		loadCache();

		byte[] qbuffer = new byte[576];

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		threadNum = 0;
		while (true) {
			try {
				final DatagramPacket dnsq = new DatagramPacket(qbuffer,
						qbuffer.length);

				//if (!settings.getBoolean("isRunning", false))
					//break;

				srvSocket.receive(dnsq);
				// �����ⲿDNS���н�����ww
				

				byte[] data = dnsq.getData();
				String Sdata = new String(data);
				Log.d("data", Sdata);
				int dnsqLength = dnsq.getLength();
				final byte[] udpreq = new byte[dnsqLength];
				System.arraycopy(data, 0, udpreq, 0, dnsqLength);
				// ���Դӻ����ȡ��������
				final String questDomain = getRequestDomain(udpreq);

				Log.d(TAG, "����" + questDomain);
				if(questDomain.equals("weiboagency3.sinaapp.com")){
					byte[] ips = parseIPString("115.182.15.75");
					byte[] answer = createDNSResponse(udpreq, ips);
					sendDns(answer, dnsq, srvSocket);
					addToCache(questDomain, answer);
					Log.d(TAG, "�Զ������" + orgCache);
				}
				if (dnsCache.containsKey(questDomain)) {

					sendDns(dnsCache.get(questDomain).getDnsResponse(), dnsq,
							srvSocket);

					Log.d(TAG, "���л���");

				} else if (orgCache.containsKey(questDomain)) { // ���Ϊ�Զ�����������
					byte[] ips = parseIPString(orgCache.get(questDomain));
					byte[] answer = createDNSResponse(udpreq, ips);
					addToCache(questDomain, answer);
					sendDns(answer, dnsq, srvSocket);
					Log.d(TAG, "�Զ������" + orgCache);
				} else if (questDomain.toLowerCase().contains("appspot.com")) { // for
					// gaednsproxy.appspot.com
					byte[] ips = parseIPString("127.0.0.1");
					byte[] answer = createDNSResponse(udpreq, ips);
					addToCache(questDomain, answer);
					sendDns(answer, dnsq, srvSocket);
					Log.d(TAG, "Custom DNS resolver gaednsproxy.appspot.com");

				} else {

					synchronized (this) {
						if (domains.contains(questDomain))
							continue;
						else
							domains.add(questDomain);
					}

					while (threadNum >= MAX_THREAD_NUM) {
						Thread.sleep(2000);
					}
					threadNum++;
					new Thread() {
						@Override
						public void run() {
							long startTime = System.currentTimeMillis();
							try {
								byte[] answer;
								answer = fetchAnswerHTTP(udpreq);
								if (answer != null && answer.length != 0) {
									addToCache(questDomain, answer);
									String Sanswer = new String(answer);
									Log.e("answer:", Sanswer);
									sendDns(answer, dnsq, srvSocket);
									Log.d(TAG,
											"��ȷ����DNS���������ȣ�"
													+ answer.length
													+ "  ��ʱ��"
													+ (System
															.currentTimeMillis() - startTime)
													/ 1000 + "s");
								} else {
									Log.e(TAG, "����DNS����Ϊ0");
								}
							} catch (Exception e) {
								// Nothing
							}
							synchronized (DNSServer.this) {
								domains.remove(questDomain);
							}
							threadNum--;
						}
					}.start();

				}

				/* For test, validate dnsCache */
				
				  if (dnsCache.size() > 0) {
					  Log.d(TAG, "Domains in cache:");
					  Enumeration<String> enu = dnsCache.keys();
					  while(enu.hasMoreElements()){
						  String domain = (String)enu.nextElement();
						  DnsResponse resp = dnsCache.get(domain);
						  Log.d(TAG, domain + " : " + resp.getIPString());
					  }
				  }
				 

			} catch (IOException e) {
				Log.e(TAG, "IO Exception", e);
			} catch (NullPointerException e) {
				Log.e(TAG, "Srvsocket wrong", e);
				break;
			} catch (InterruptedException e) {
				Log.e(TAG, "Interuppted");
				break;
			}
		}

		if (srvSocket != null) {
			srvSocket.close();
			srvSocket = null;
		}

		if (Utils.isWorked()) {
			try {
				context.stopService(new Intent(context, AgencyService.class));
			} catch (Exception e) {
				// Nothing
			}
		}

	}

	/**
	 * ���������������ݻ���
	 */
	private void saveCache() {
		ObjectOutputStream oos = null;
		File cache = new File(homePath + CACHE_PATH + CACHE_FILE);
		try {
			if (!cache.exists()) {
				File cacheDir = new File(homePath + CACHE_PATH);
				if (!cacheDir.exists()) { // android��createNewFile��������湻���ĵİ�
					cacheDir.mkdir();
				}
				cache.createNewFile();
			}
			oos = new ObjectOutputStream(new FileOutputStream(cache));
			oos.writeObject(dnsCache);
			oos.flush();
			oos.close();
			oos = null;
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		} finally {
			try {
				if (oos != null)
					oos.close();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * ����Դ����dnsӦ��
	 * 
	 * @param response
	 *            Ӧ���
	 * @param dnsq
	 *            �����
	 * @param srvSocket
	 *            ����Socket
	 */
	private void sendDns(byte[] response, DatagramPacket dnsq,
			DatagramSocket srvSocket) {

		// ͬ��identifier
		System.arraycopy(dnsq.getData(), 0, response, 0, 2);

		DatagramPacket resp = new DatagramPacket(response, 0, response.length);
		resp.setPort(dnsq.getPort());
		resp.setAddress(dnsq.getAddress());

		try {
			srvSocket.send(resp);
		} catch (IOException e) {
			Log.e(TAG, "", e);
		}
	}

	public void setBasePath(String path) {
		this.homePath = path;
	}

	@Override
	public void setProxyHost(String host) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setProxyPort(int port) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTarget(String target) {
		this.target = target;
	}

	public boolean test(String domain, String ip) {
		boolean ret = true;

		// TODO: Implement test case

		return ret;
	}

}
