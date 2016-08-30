/**
 * ����������Ŀ
 * ���������������豸
 */
package com.gxu.bluetoothchat;

import java.util.Set;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class DeviceListActivity extends Activity
{

	private static final String TAG = "DeviceListActivity";
	private static final boolean D = true;

	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter mBtAdapter;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private ArrayAdapter<String> mNewDevicesArrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//Ϊ���������ϼ���Բ�ν�����
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);
		//���ùرյ�ǰActivity�ķ���ֵ������û�ͨ������Ӳ�����ؼ��ر�Activity�������ڵ���finish��ǰû�е���setResult����ô
		//����뽫����ΪRESULT_CANCELED�����Intent������Ϊnull����
		setResult(Activity.RESULT_CANCELED);
		//��ʼ��������ť
		Button scanButton = (Button) findViewById(R.id.button_scan);
		//ɨ�谴ť������
		scanButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				//ɨ��
				doDiscovery();
				//ɨ��֮��Ӱ��ɨ�谴ť
				v.setVisibility(View.GONE);
			}
		});
		//��ʼ�����ڱ�������Ե������豸��ArrayAdapter����
		mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);
		//��ʼ�����ڱ������������������豸��ArrayAdapter����
		mNewDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);

		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);
		//ע�����ڽ������������������豸��Receiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		//ע��������ɵĽ�����
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);

		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		//��õ�ǰ����Ե��豸����set���ϱ���
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
		//�����ǰ���Ѿ���Ե��豸��������ʾ���б���
		if (pairedDevices.size() > 0)
		{
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			for (BluetoothDevice device : pairedDevices)
			{
				mPairedDevicesArrayAdapter.add(device.getName() + "\n"
						+ device.getAddress());
			}
		}
		//�����ǰû���Ѿ���Ե��豸
		else
		{
			String noDevices = getResources().getText(R.string.none_paired)
					.toString();
			mPairedDevicesArrayAdapter.add(noDevices);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if (mBtAdapter != null)
		{
			mBtAdapter.cancelDiscovery();
		}
		this.unregisterReceiver(mReceiver);
	}
	//ɨ��
	private void doDiscovery()
	{
		if (D)
			Log.d(TAG, "doDiscovery()");
		//����Բ�ν������ɼ�
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
		//�������ɨ�裬��ֹͣ�����¿�ʼɨ��
		if (mBtAdapter.isDiscovering())
		{
			mBtAdapter.cancelDiscovery();
		}
		mBtAdapter.startDiscovery();
	}
	//�����б���ĵ���¼�
	private OnItemClickListener mDeviceClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3)
		{
			mBtAdapter.cancelDiscovery();

			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
			//���һ��activityҪ�������ݵ����������Ǹ�activity�����Ե���setResult()������
			//����setResult()����������finish()֮ǰ��
			setResult(Activity.RESULT_OK, intent);
			
			finish();
		}
	};
	//���ù㲥�����������ڴ����������������豸�Լ�������ɵĶ�����
	private final BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			//ÿ����һ���豸����ִ������Ĵ���
			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{
				//���BluetoothDevice����
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				//������豸�Ѿ���ԣ�����Ը��豸
				if (device.getBondState() != BluetoothDevice.BOND_BONDED)
				{
					//�豸δ��ԣ�������ӵ�mNewDevicesArrayAdapter�����У��Ա����ʾ���б���
					mNewDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
			}
			//�������ʱִ������Ĵ���
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				//������ɣ�����Բ�ν��������ɼ�
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				//���δ�������κ��豸���ڵ�ǰ������ʾ��ʾ��Ϣ
				if (mNewDevicesArrayAdapter.getCount() == 0)
				{
					String noDevices = getResources().getText(
							R.string.none_found).toString();
					//����ʾ��Ϣ��δ���������豸����ӵ���������
					mNewDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

}
