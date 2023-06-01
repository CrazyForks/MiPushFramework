package top.trumeet.mipushframework.register;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.xiaomi.xmsf.R;

import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.register.RegisteredApplication;
import top.trumeet.mipushframework.permissions.ManagePermissionsActivity;
import top.trumeet.mipushframework.utils.BaseAppsBinder;
import top.trumeet.mipushframework.utils.ParseUtils;

/**
 * Created by Trumeet on 2017/8/26.
 * @author Trumeet
 */

public class RegisteredApplicationBinder extends BaseAppsBinder<RegisteredApplication> {
    RegisteredApplicationBinder() {
        super();
    }

    @Override
    protected void onBindViewHolder(@NonNull final ViewHolder holder
            , @NonNull final RegisteredApplication item) {
        Context context = holder.itemView.getContext();
        fillData(item.getPackageName(), true,
                holder);
        //todo res color
        int ErrorColor = Color.parseColor("#FFF41804");
        holder.status.setText(null);
        if (!item.existServices) {
            holder.status.setText("MiPush Services not found");
            holder.status.setTextColor(ErrorColor);
        }
        holder.summary.setText(null);
        if (item.lastReceiveTime.getTime() != 0) {
            holder.summary.setText(String.format("%s%s",
                    context.getString(R.string.last_receive),
                    ParseUtils.getFriendlyDateString(item.lastReceiveTime, Utils.getUTC(), context)));
        }
        switch (item.getRegisteredType()) {
            case 1: {
                holder.text2.setText(R.string.app_registered);
                holder.text2.setTextColor(Color.parseColor("#FF0B5B27"));
                break;
            }
            case 2: {
                holder.text2.setText(R.string.app_registered_error);
                holder.text2.setTextColor(ErrorColor);
                break;
            }
            case 0: {
                holder.text2.setText(R.string.status_app_not_registered);
                holder.text2.setTextColor(holder.title.getTextColors());
                break;
            }
        }
        holder.itemView.setOnClickListener(view -> context
                .startActivity(new Intent(context,
                        ManagePermissionsActivity.class)
                .putExtra(ManagePermissionsActivity.EXTRA_PACKAGE_NAME,
                        item.getPackageName())
                .putExtra(ManagePermissionsActivity.EXTRA_IGNORE_NOT_REGISTERED, true)));
    }
}
