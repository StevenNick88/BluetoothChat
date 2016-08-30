/**
 * ����������Ŀ
 * ���ͺͽ�������������Ϣ
 */
package com.gxu.bluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothChatService
{	
	private static final String TAG = "BluetoothChatService";
	private static final boolean D = true;

	private static final String NAME = "BluetoothChat";
	//UUID�൱�ڶ˿�
	private static final UUID MY_UUID = UUID
			.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	public static final int STATE_NONE = 0;
	public static final int STATE_LISTEN = 1; 
												
	public static final int STATE_CONNECTING = 2;
													
	public static final int STATE_CONNECTED = 3; 
	//���췽������ʼ�������豸������״̬�Լ�ʵ����Handler
	public BluetoothChatService(Context context, Handler handler)
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}
	//��������״̬��һ���豸��״̬��Ϊ4�֣�STATE_NONE   STATE_LISTEN   STATE_CONNECTING   STATE_CONNECTED
	private synchronized void setState(int state)
	{
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;
		/**
		 *  ʹ��Message��ʱ����ʹ�� Message msg = handler.obtainMessage()����ʽ��
		   	��Message����Ҫ�Լ�New Message ���������ǵ�Message �Ѿ����� �Լ���������,���Ǵ�MessagePool �õ�,
		   	ʡȥ�˴������������ڴ�Ŀ�������������
		   	.sendToTarget�����������target����handler��sendToTarget()���ڵ���handler�� sendMessage����
		 */
		mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}
	//�������״̬
	public synchronized int getState()
	{
		return mState;
	}
	/**
	 * ����������BluetoothChatService�����service�л���3�����̣��ֱ�������ʾ�������ӣ��������Ӻͼ������̡߳�
	 * �������񣬴���AcceptThread�߳��������������޸�״̬ΪSTATE_LISTEN ,
	 */
	public synchronized void start()
	{
		if (D)
			Log.d(TAG, "start");

		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		if (mAcceptThread == null)
		{
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
		setState(STATE_LISTEN);
	}
	//��������
	public synchronized void connect(BluetoothDevice device)
	{
		if (D)
			Log.d(TAG, "connect to: " + device);

		if (mState == STATE_CONNECTING)
		{
			if (mConnectThread != null)
			{
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);
	}

	//�������ӣ�connected�����У��ᴴ��ConnectedThread�̣߳����ҷ����豸��name�������handler���д����޸�״̬Ϊ
	//STATE_CONNECTED��ConnectedThread�Ĵ����ᴴ��socket����������������Ҷ�ȡͨ���е���Ϣ�����͸�handler�� 
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device)
	{
		if (D)
			Log.d(TAG, "connected");

		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;
		}

		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		setState(STATE_CONNECTED);
	}
	//ֹͣ���񣬹ر��߳�
	public synchronized void stop()
	{
		if (D)
			Log.d(TAG, "stop");
		if (mConnectThread != null)
		{
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null)
		{
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		if (mAcceptThread != null)
		{
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		setState(STATE_NONE);
	}

	public void write(byte[] out)
	{
		ConnectedThread r;
		synchronized (this)
		{
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		r.write(out);
	}
	
	//����ʧ��
	private void connectionFailed()
	{
		setState(STATE_LISTEN);

		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	//�����ж�
	private void connectionLost()
	{
		setState(STATE_LISTEN);

		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	/**
	 * ����AcceptThread�߳�����������AcceptThread�ᴴ��BluetoothServerSocket 
	 *
	 */
	private class AcceptThread extends Thread
	{
		//���������ServerSocket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread()
		{
			BluetoothServerSocket tmp = null;
			//����һ����ʱ�����������ServerSocket
			try
			{
				//����һ����������ȫ��RFCOMM�����׽���������¼�����Ӵ��׽��ֵ�Զ���豸������֤���ڴ��׽���ͨ�Ž������ܡ�
				//RFCOMM��һ���򵥴���Э�飬��Ŀ��Ϊ�˽�������������ͬ�豸�ϵ�Ӧ�ó���֮�䱣֤һ��������ͨ��·����
				//��������֮�䱣��һͨ�Ŷε����⡣
				//UUID��ָ��һ̨���������ɵ����֣�����֤����ͬһʱ���е����л�������Ψһ�ġ�
				tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "listen() failed", e);
			}
			mmServerSocket = tmp;
		}

		public void run()
		{
			if (D)
				Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");
			BluetoothSocket socket = null;
			//���������豸�Ƿ�����
			while (mState != STATE_CONNECTED)
			{
				try
				{
					//������ӳɹ������������ͻ���Socket����
					socket = mmServerSocket.accept();
				}
				catch (IOException e)
				{
					Log.e(TAG, "accept() failed", e);
					break;
				}
				/**
				 * �߳����е�ʱ�����BluetoothSocket  socket=serverSocket.accept()ȥ��������ʱ������豸������״
				        ���в�ͬ����������Ѿ����ӻ�none״̬����ر�socket����STATE_LISTEN  ��STATE_CONNECTING��ʱ�򣬻�
				        ����connected(BluetoothSocket socket,BluetoothDevice device)��
				 */
				if (socket != null)
				{
					synchronized (BluetoothChatService.this)
					{
						//�������״̬
						switch (mState)
						{
							case STATE_LISTEN:
							case STATE_CONNECTING:
								//��ʼ��������������ݵ��߳�
								connected(socket, socket.getRemoteDevice());
								break;
							case STATE_NONE:
							case STATE_CONNECTED:
								//�ر�����Socket
								try
								{
									socket.close();
								}
								catch (IOException e)
								{
									Log.e(TAG,"Could not close unwanted socket",e);
								}
								break;
						}
					}
				}
			}
			if (D)
				Log.i(TAG, "END mAcceptThread");
		}
		//�ر����������Socket
		public void cancel()
		{
			if (D)
				Log.d(TAG, "cancel " + this);
			try
			{
				mmServerSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}
	/**
	 * ��ConnectThread�߳��У���ʾ���豸��ͼ���ӡ�������ʱ������device��createRfcommSocketToServiceRecord��MY_UUID��
	 * �����õ�socket����������ʱ��ȥconnect����������BluetoothChatService�������ͻ���õ����������õ���һ�������豸��ͨѶ��Ч����
	 */
	private class ConnectThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		
		//���캯��
		public ConnectThread(BluetoothDevice device)
		{
			mmDevice = device;
			BluetoothSocket tmp = null;
			
			try
			{
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "create() failed", e);
			}
			mmSocket = tmp;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN mConnectThread");
			setName("ConnectThread");
			//ֹͣɨ�������豸
			mAdapter.cancelDiscovery();

			try
			{
				mmSocket.connect();
			}
			catch (IOException e)
			{
				//����ʧ��
				connectionFailed();
				try
				{
					mmSocket.close();
				}
				catch (IOException e2)
				{
					Log.e(TAG,"unable to close() socket during connection failure",e2);
				}
				BluetoothChatService.this.start();
				return;
			}

			synchronized (BluetoothChatService.this)
			{
				mConnectThread = null;
			}
			connected(mmSocket, mmDevice);
		}
		//�رտͻ���Socket
		public void cancel()
		{
			try
			{
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
	/**
	 * ConnectedThread�Ĵ����ᴴ��socket����������������Ҷ�ȡͨ���е���Ϣ�����͸�handler�� 
	 */
	private class ConnectedThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket)
		{
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			//�������Socket��InputStream��OutputStream����
			try
			{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}
			catch (IOException e)
			{
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;
			//���ϼ���InputStream��һ�������ͻ���Socket��������Ϣ���ͻ��������յ�
			while (true)
			{
				try
				{
					//��ȡ�����ͻ��˷��͹�������Ϣ�����δ�����κ���Ϣ��������䱻����
					bytes = mmInStream.read(buffer);
					
					/**
					 *  ʹ��Message��ʱ����ʹ�� Message msg = handler.obtainMessage()����ʽ��
					   	��Message����Ҫ�Լ�New Message ���������ǵ�Message �Ѿ����� �Լ���������,���Ǵ�MessagePool �õ�,
					   	ʡȥ�˴������������ڴ�Ŀ�������������
					   	.sendToTarget�����������target����handler��sendToTarget()���ڵ���handler�� sendMessage����
					 */
					//������Ϣ�����������¼�б��е����ݡ���Ϣ�Ĵ�����BluetoothChat���handleMessage������
					mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes,
							-1, buffer).sendToTarget();
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);
					//�����ж�
					connectionLost();
					break;
				}
			}
		}
		//���������ӵ������豸�����ֽ�����
		public void write(byte[] buffer)
		{
			try
			{
				mmOutStream.write(buffer);
				/**
				 *  ʹ��Message��ʱ����ʹ�� Message msg = handler.obtainMessage()����ʽ��
				   	��Message����Ҫ�Լ�New Message ���������ǵ�Message �Ѿ����� �Լ���������,���Ǵ�MessagePool �õ�,
				   	ʡȥ�˴������������ڴ�Ŀ�������������
				   	.sendToTarget�����������target����handler��sendToTarget()���ڵ���handler�� sendMessage����
				 */
				//������Ϣ�����������¼�б��е����ݡ���Ϣ�Ĵ�����BluetoothChat���handleMessage������
				mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during write", e);
			}
		}
		//�ر�socket
		public void cancel()
		{
			try
			{
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}
