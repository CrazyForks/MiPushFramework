package top.trumeet.mipushframework.register;

import static top.trumeet.common.Constants.TAG;

import android.app.SearchManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.github.promeg.pinyinhelper.Pinyin;
import com.xiaomi.xmsf.R;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import me.drakeet.multitype.Items;
import me.drakeet.multitype.MultiTypeAdapter;
import top.trumeet.common.cache.ApplicationNameCache;
import top.trumeet.mipush.provider.db.EventDb;
import top.trumeet.mipush.provider.db.RegisteredApplicationDb;
import top.trumeet.mipush.provider.register.RegisteredApplication;
import top.trumeet.mipush.provider.register.RegisteredApplication.RegisteredType;
import top.trumeet.mipushframework.utils.MiPushManifestChecker;
import top.trumeet.mipushframework.widgets.Footer;
import top.trumeet.mipushframework.widgets.FooterItemBinder;

/**
 * Created by Trumeet on 2017/8/26.
 *
 * @author Trumeet
 */

public class RegisteredApplicationFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final Logger logger = XLog.tag(RegisteredApplicationFragment.class.getSimpleName()).build();
    private MultiTypeAdapter mAdapter;
    private LoadTask mLoadTask;
    private String mQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new MultiTypeAdapter();
        mAdapter.register(RegisteredApplication.class, new RegisteredApplicationBinder());
        mAdapter.register(Footer.class, new FooterItemBinder());
        setHasOptionsMenu(true);
    }

    SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RecyclerView view = new RecyclerView(getActivity());
        view.setLayoutManager(new LinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));
        view.setAdapter(mAdapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(view.getContext(),
                LinearLayoutManager.VERTICAL);
        view.addItemDecoration(dividerItemDecoration);


        swipeRefreshLayout = new SwipeRefreshLayout(getActivity());
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.addView(view);

        loadPage();
        return swipeRefreshLayout;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.findItem(R.id.action_enable).setVisible(false);
        menu.findItem(R.id.action_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchItem.setVisible(true);

        initSearchBar(searchItem);
    }

    private void initSearchBar(MenuItem searchItem) {
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.equals(mQuery)) {
                    return true;
                }
                mQuery = newText.toLowerCase();
                onRefresh();
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String newText) {
                return true;
            }
        });
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadPage();
    }

    private void loadPage() {
        Log.d(TAG, "loadPage");
        if (mLoadTask != null && !mLoadTask.isCancelled()) {
            return;
        }
        swipeRefreshLayout.setRefreshing(true);
        mLoadTask = new LoadTask(getActivity());
        mLoadTask.execute();
    }

    @Override
    public void onDetach() {
        if (mLoadTask != null && !mLoadTask.isCancelled()) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        super.onDetach();
    }

    @Override
    public void onRefresh() {
        loadPage();

    }

    private class LoadTask extends AsyncTask<Integer, Void, LoadTask.Result> {
        private CancellationSignal mSignal;

        public LoadTask(Context context) {
            this.context = context;
        }

        private Context context;

        class Result {
            private final int notUseMiPushCount;
            private final List<RegisteredApplication> list;

            public Result(int notUseMiPushCount, List<RegisteredApplication> list) {
                this.notUseMiPushCount = notUseMiPushCount;
                this.list = list;
            }
        }

        class ElapsedTimer {
            private long start = System.currentTimeMillis();
            public long start() {
                return start = System.currentTimeMillis();
            };
            public long restart() {
                long old = start;
                start = System.currentTimeMillis();
                return start - old;
            };
            public long elapsed() {
                return System.currentTimeMillis() - start;
            };
        }

        @Override
        protected Result doInBackground(Integer... integers) {
            // TODO: Sharing/Modular actuallyRegisteredPkgs to doInBackground of ManagePermissionsActivity.java
            mSignal = new CancellationSignal();

            logger.d("[loadApp] start load app list");
            ElapsedTimer totalTimer = new ElapsedTimer();
            ElapsedTimer timer = new ElapsedTimer();
            Map<String /* pkg */, RegisteredApplication> registeredPkgs = new HashMap<>();
            for (RegisteredApplication application : RegisteredApplicationDb.getList(null)) {
                registeredPkgs.put(application.getPackageName(), application);
            }
            logger.d("[loadApp] get registeredPkgs ms: %d", timer.restart());

            Set<String> registeredPkgsFromEvents = EventDb.queryRegistered();
            logger.d("[loadApp] get actuallyRegisteredPkgs ms: %d", timer.restart());

            MiPushManifestChecker checker = null;
            try {
                checker = MiPushManifestChecker.create(context);
            } catch (PackageManager.NameNotFoundException | ClassNotFoundException | NoSuchMethodException e) {
                Log.e(RegisteredApplicationFragment.class.getSimpleName(), "Create mi push checker", e);
            }

            final List<PackageInfo> packageInfos = context.getPackageManager().getInstalledPackages(
                            PackageManager.GET_DISABLED_COMPONENTS |
                            PackageManager.GET_SERVICES |
                            PackageManager.GET_RECEIVERS);
            final int totalPkg = packageInfos.size();
            logger.d("[loadApp] get package info ms: %d", timer.restart());

            MiPushManifestChecker finalChecker = checker;
            for (final Iterator<PackageInfo> iterator = packageInfos.iterator(); iterator.hasNext(); ) {
                PackageInfo info = iterator.next();
                if (!((info.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0 &&
                        (registeredPkgs.containsKey(info.applicationInfo.packageName) ||
                                finalChecker != null && finalChecker.checkServices(info)))) {
                    iterator.remove();
                }
            }
            logger.d("filter not service package ms: %d", timer.restart());

            List<RegisteredApplication> res = new Vector<>();
            for (PackageInfo info : packageInfos) {
                String currentAppPkgName = info.packageName;
                RegisteredApplication application;
                if (registeredPkgs.containsKey(currentAppPkgName)) {
                    application = registeredPkgs.get(currentAppPkgName);
                    application.setRegisteredType(registeredPkgsFromEvents.contains(currentAppPkgName) ?
                            RegisteredType.Registered :
                            RegisteredType.Unregistered);
                    res.add(application);
                } else {
                    // checkReceivers will use Class#forName, but we can't change our classloader to target app's.
                    application = new RegisteredApplication();
                    application.setPackageName(currentAppPkgName);
                    application.setRegisteredType(RegisteredType.NotRegistered);
                    res.add(application);
                }
                application.existServices = finalChecker != null && finalChecker.checkServices(info);
            }
            logger.d("[loadApp] convert to application list ms: %d", timer.restart());

            for (RegisteredApplication application : res) {
                application.appName = ApplicationNameCache.getInstance()
                        .getAppName(context, application.getPackageName()).toString();
            }
            logger.d("[loadApp] query name ms: %d", timer.restart());

            for (RegisteredApplication application : res) {
                application.appNamePinYin = Pinyin.toPinyin(application.appName, "");
            }
            logger.d("[loadApp] query pinyin ms: %d", timer.restart());

            for (RegisteredApplication application : res) {
                application.lastReceiveTime = new Date(EventDb.getLastReceiveTime(application.getPackageName()));
            }
            logger.d("[loadApp] query lastReceiveTime ms: %d", timer.restart());

            for (final Iterator<RegisteredApplication> iterator = res.iterator(); iterator.hasNext(); ) {
                RegisteredApplication info = iterator.next();
                if (!(info.getPackageName().toLowerCase().contains(mQuery) ||
                        info.appName.toLowerCase().contains(mQuery) ||
                        info.appNamePinYin.contains(mQuery)
                )) {
                    iterator.remove();
                }
            }
            logger.d("[loadApp] filter app search ms: %d", timer.restart());

            Collections.sort(res, (o1, o2) -> {
                if (o1.getId() == null && o2.getId() == null) {
                    return o1.appNamePinYin.compareTo(o2.appNamePinYin);
                }

                if (o1.getId() == null) {
                    return 1;
                }

                if (o2.getId() == null) {
                    return -1;
                }

                if (o1.getRegisteredType() != o2.getRegisteredType()) {
                    return o1.getRegisteredType() - o2.getRegisteredType();
                }
                int cmp = o2.lastReceiveTime.compareTo(o1.lastReceiveTime);
                if (cmp != 0) {
                    return cmp;
                }
                return o1.appNamePinYin.compareTo(o2.appNamePinYin);
            });
            logger.d("[loadApp] sort application list will show ms: %d", timer.restart());
            logger.d("[loadApp] end load app list ms: %d", totalTimer.elapsed());

            int notUseMiPushCount = totalPkg - registeredPkgs.size();
            return new Result(notUseMiPushCount, res);
        }

        @Override
        protected void onPostExecute(Result result) {
            mAdapter.notifyItemRangeRemoved(0, mAdapter.getItemCount());
            mAdapter.getItems().clear();

            int start = mAdapter.getItemCount();
            Items items = new Items(mAdapter.getItems());
            items.addAll(result.list);
            if (result.notUseMiPushCount > 0) {
                items.add(new Footer(getString(R.string.footer_app_ignored_not_registered, Integer.toString(result.notUseMiPushCount))));
            }
            mAdapter.setItems(items);
            mAdapter.notifyItemRangeInserted(start, result.notUseMiPushCount > 0 ? result.list.size() + 1 : result.list.size());

            swipeRefreshLayout.setRefreshing(false);
            mLoadTask = null;
        }

        @Override
        protected void onCancelled() {
            if (mSignal != null) {
                if (!mSignal.isCanceled()) {
                    mSignal.cancel();
                }
                mSignal = null;
            }

            swipeRefreshLayout.setRefreshing(false);
            mLoadTask = null;
        }
    }
}
