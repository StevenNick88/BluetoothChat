/**
 * 蓝牙聊天项目
 * 发送和接收蓝牙聊天信息
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
	//UUID相当于端口
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
	//构造方法：初始化蓝牙设备、连接状态以及实例化Handler
	public BluetoothChatService(Context context, Handler handler)
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}
	//设置连接状态：一个设备的状态分为4种：STATE_NONE   STATE_LISTEN   STATE_CONNECTING   STATE_CONNECTED
	private synchronized void setState(int state)
	{
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;
		/**
		 *  使用Message的时候尽量使用 Message msg = handler.obtainMessage()的形式创
		   	建Message，不要自己New Message ，这里我们的Message 已经不是 自己创建的了,而是从MessagePool 拿的,
		   	省去了创建对象申请内存的开销。。。。。
		   	.sendToTarget方法：这里的target就是handler，sendToTarget()又在调用handler的 sendMessage方法
		 */
		mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}
	//获得连接状态
	public synchronized int getState()
	{
		return mState;
	}
	/**
	 * 创建并启动BluetoothChatService，这个service中会有3个进程，分别用来表示尝试连接，正在连接和监听的线程。
	 * 启动服务，创建AcceptThread线程用来监听，并修改状态为STATE_LISTEN ,
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
	//尝试连接
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

	//正在连接：connected方法中，会创建ConnectedThread线程，并且发送设备的name给自身的handler进行处理，修改状态为
	//STATE_CONNECTED。ConnectedThread的创建会创建socket的输入输出流，并且读取通道中的消息，发送给handler。 
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
	//停止服务，关闭线程
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
	
	//连接失败
	private void connectionFailed()
	{
		setState(STATE_LISTEN);

		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	//连接中断
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
	 * 创建AcceptThread线程用来监听，AcceptThread会创建BluetoothServerSocket 
	 *
	 */
	private class AcceptThread extends Thread
	{
		//蓝牙服务端ServerSocket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread()
		{
			BluetoothServerSocket tmp = null;
			//创建一个临时的蓝牙服务端ServerSocket
			try
			{
				//创建一个监听，安全的RFCOMM蓝牙套接字与服务记录。连接此套接字的远程设备将被认证并在此套接字通信将被加密。
				//RFCOMM是一个简单传输协议，其目的为了解决如何在两个不同设备上的应用程序之间保证一条完整的通信路径，
				//并在它们之间保持一通信段的问题。
				//UUID是指在一台机器上生成的数字，它保证对在同一时空中的所有机器都是唯一的。
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
			//监听蓝牙设备是否被连接
			while (mState != STATE_CONNECTED)
			{
				try
				{
					//如果连接成功，返回蓝牙客户端Socket对象
					socket = mmServerSocket.accept();
				}
				catch (IOException e)
				{
					Log.e(TAG, "accept() failed", e);
					break;
				}
				/**
				 * 线程运行的时候调用BluetoothSocket  socket=serverSocket.accept()去监听，此时会根据设备的连接状
				        进行不同操作，如何已经连接或none状态，则关闭socket，若STATE_LISTEN  和STATE_CONNECTING的时候，会
				        调用connected(BluetoothSocket socket,BluetoothDevice device)。
				 */
				if (socket != null)
				{
					synchronized (BluetoothChatService.this)
					{
						//处理各种状态
						switch (mState)
						{
							case STATE_LISTEN:
							case STATE_CONNECTING:
								//开始处理输入输出数据的线程
								connected(socket, socket.getRemoteDevice());
								break;
							case STATE_NONE:
							case STATE_CONNECTED:
								//关闭蓝牙Socket
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
		//关闭蓝牙服务端Socket
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
	 * 在ConnectThread线程中，表示有设备试图连接。创建的时候会调用device的createRfcommSocketToServiceRecord（MY_UUID）
	 * 方法得到socket对象，启动的时候去connect，并且启动BluetoothChatService，这样就会调用到第三部，得到了一个蓝牙设备间通讯的效果。
	 */
	private class ConnectThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		
		//构造函数
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
			//停止扫描蓝牙设备
			mAdapter.cancelDiscovery();

			try
			{
				mmSocket.connect();
			}
			catch (IOException e)
			{
				//连接失败
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
		//关闭客户端Socket
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
	 * ConnectedThread的创建会创建socket的输入输出流，并且读取通道中的消息，发送给handler。 
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
			//获得蓝牙Socket的InputStream和OutputStream对象
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
			//不断监视InputStream，一旦蓝牙客户端Socket发过来信息，就会立即接收到
			while (true)
			{
				try
				{
					//读取蓝牙客户端发送过来的信息，如果未发送任何信息，该条语句被阻塞
					bytes = mmInStream.read(buffer);
					
					/**
					 *  使用Message的时候尽量使用 Message msg = handler.obtainMessage()的形式创
					   	建Message，不要自己New Message ，这里我们的Message 已经不是 自己创建的了,而是从MessagePool 拿的,
					   	省去了创建对象申请内存的开销。。。。。
					   	.sendToTarget方法：这里的target就是handler，sendToTarget()又在调用handler的 sendMessage方法
					 */
					//发送消息，更新聊天记录列表中的内容。消息的处理在BluetoothChat类的handleMessage方法中
					mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes,
							-1, buffer).sendToTarget();
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);
					//连接中断
					connectionLost();
					break;
				}
			}
		}
		//向与我连接的蓝牙设备发送字节数据
		public void write(byte[] buffer)
		{
			try
			{
				mmOutStream.write(buffer);
				/**
				 *  使用Message的时候尽量使用 Message msg = handler.obtainMessage()的形式创
				   	建Message，不要自己New Message ，这里我们的Message 已经不是 自己创建的了,而是从MessagePool 拿的,
				   	省去了创建对象申请内存的开销。。。。。
				   	.sendToTarget方法：这里的target就是handler，sendToTarget()又在调用handler的 sendMessage方法
				 */
				//发送消息，更新聊天记录列表中的内容。消息的处理在BluetoothChat类的handleMessage方法中
				mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during write", e);
			}
		}
		//关闭socket
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
