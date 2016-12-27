package com.donute.bluetoothchat;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;

/**
 * Created by zhouyufei on 2016/12/26.
 */

public class DeviceViewAdapter extends BaseAdapter {
    private Context context;
    private List<BluetoothDevice> devices;
    private LayoutInflater inflater;

    public DeviceViewAdapter(Context context, List<BluetoothDevice> devices) {
        this.context = context;
        this.devices = devices;
        inflater=LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView==null){
            holder=new ViewHolder();
            convertView=inflater.inflate(R.layout.item_device,null);
            holder.tvName= (TextView) convertView.findViewById(R.id.tv_devices_name);
            holder.tvAddr= (TextView) convertView.findViewById(R.id.tv_devices_addr);
            holder.tvState= (TextView) convertView.findViewById(R.id.tv_devices_state);
            convertView.setTag(holder);
        }else {
            holder= (ViewHolder) convertView.getTag();
        }
        BluetoothDevice device=devices.get(position);
        holder.tvName.setText(device.getName());
        holder.tvAddr.setText(device.getAddress());
        switch (device.getBondState()){
            case BOND_NONE:
                holder.tvState.setText("未绑定");
                break;
            case BOND_BONDING:
                holder.tvState.setText("正在绑定");
                break;
            case BOND_BONDED:
                holder.tvState.setText("已经绑定");
                break;
        }

        return convertView;
    }
    static class ViewHolder{
        TextView tvName;
        TextView tvAddr;
        TextView tvState;
    }
}
