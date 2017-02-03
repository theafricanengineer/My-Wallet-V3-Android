package piuk.blockchain.android.ui.balance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.List;

import piuk.blockchain.android.R;
import piuk.blockchain.android.data.payload.PayloadBridge;
import piuk.blockchain.android.databinding.FragmentBalanceBinding;
import piuk.blockchain.android.ui.backup.BackupWalletActivity;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.ui.home.MainActivity;
import piuk.blockchain.android.ui.home.SecurityPromptDialog;
import piuk.blockchain.android.ui.home.TransactionSelectedListener;
import piuk.blockchain.android.ui.settings.SettingsActivity;
import piuk.blockchain.android.ui.settings.SettingsFragment;
import piuk.blockchain.android.ui.transactions.TransactionDetailActivity;
import piuk.blockchain.android.util.DateUtil;
import piuk.blockchain.android.util.ExchangeRateFactory;
import piuk.blockchain.android.util.ListUtil;
import piuk.blockchain.android.util.MonetaryUtil;
import piuk.blockchain.android.util.PrefsUtil;
import piuk.blockchain.android.util.ViewUtils;
import piuk.blockchain.android.util.annotations.Thunk;

public class BalanceFragment extends Fragment implements BalanceViewModel.DataListener, TransactionSelectedListener {

    public static final String ACTION_INTENT = "info.blockchain.wallet.ui.BalanceFragment.REFRESH";
    public static final String KEY_TRANSACTION_LIST_POSITION = "transaction_list_position";
    public static final String KEY_TRANSACTION_HASH = "transaction_hash";
    public static final int SHOW_BTC = 1;
    private static final int SHOW_FIAT = 2;
    private int balanceDisplayState = SHOW_BTC;
    public int balanceBarHeight;
    private BalanceHeaderAdapter accountsAdapter;
    @Thunk Communicator comm;
    @Thunk boolean isBTC = true;
    // Accounts list
    @Thunk AppCompatSpinner accountSpinner;
    // Tx list
    @Thunk BalanceListAdapter transactionAdapter;
    private DateUtil dateUtil;

    @Thunk FragmentBalanceBinding binding;
    @Thunk BalanceViewModel viewModel;
    @Thunk AppBarLayout appBarLayout;

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction().equals(ACTION_INTENT) && getActivity() != null) {
                binding.swipeContainer.setRefreshing(true);
                viewModel.updateAccountList();
                viewModel.updateBalanceAndTransactionList(intent, accountSpinner.getSelectedItemPosition(), isBTC);
                transactionAdapter.onTransactionsUpdated(viewModel.getTransactionList());
                binding.swipeContainer.setRefreshing(false);
                binding.rvTransactions.getAdapter().notifyDataSetChanged();
                binding.rvTransactions.scrollToPosition(0);
                // Check backup status on receiving funds
                viewModel.onViewReady();
                binding.rvTransactions.scrollToPosition(0);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_balance, container, false);
        viewModel = new BalanceViewModel(this);
        dateUtil = new DateUtil(getContext());

        balanceDisplayState = viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_BALANCE_DISPLAY_STATE, SHOW_BTC);
        isBTC = balanceDisplayState != SHOW_FIAT;

        balanceBarHeight = (int) getResources().getDimension(R.dimen.balance_bar_height);

        setupViews();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.onViewReady();
    }

    private void setAccountSpinner() {
        appBarLayout = (AppBarLayout) getActivity().findViewById(R.id.appbar_layout);
        ((AppCompatActivity) getContext()).setSupportActionBar((Toolbar) getActivity().findViewById(R.id.toolbar_general));

        if (viewModel.getActiveAccountAndAddressList().size() > 1) {
            accountSpinner.setVisibility(View.VISIBLE);
        } else if (viewModel.getActiveAccountAndAddressList().size() > 0) {
            accountSpinner.setSelection(0);
            accountSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        comm.resetNavigationDrawer();

        IntentFilter filter = new IntentFilter(ACTION_INTENT);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, filter);

        viewModel.updateAccountList();
        viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);

        binding.rvTransactions.clearOnScrollListeners();
        binding.rvTransactions.addOnScrollListener(new CollapseActionbarScrollListener() {
            @Override
            public void onMoved(int distance) {
                setToolbarOffset(distance);
            }
        });

        String fiat = viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        double lastPrice = ExchangeRateFactory.getInstance().getLastPrice(fiat);

        if (transactionAdapter != null) {
            transactionAdapter.notifyAdapterDataSetChanged(lastPrice);
        }

        if (accountsAdapter != null) {
            accountsAdapter.notifyFiatUnitsChanged(fiat, lastPrice);
        }
    }

    @Override
    public void updateBalance(String balance) {
        binding.balance.setText(balance);
    }

    @Override
    public void setShowRefreshing(boolean showRefreshing) {
        binding.swipeContainer.setRefreshing(showRefreshing);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
    }

    /**
     * Deprecated, but necessary to prevent casting issues on <API21
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        comm = (Communicator) activity;
    }

    @Override
    public void show2FaDialog() {
        SecurityPromptDialog securityPromptDialog = SecurityPromptDialog.newInstance(
                R.string.two_fa,
                getString(R.string.security_centre_two_fa_message),
                R.drawable.vector_mobile,
                R.string.enable,
                true,
                true
        );

        securityPromptDialog.setPositiveButtonListener(v -> {
            securityPromptDialog.dismiss();
            if (securityPromptDialog.isChecked()) {
                viewModel.neverPrompt2Fa();
            }
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            intent.putExtra(SettingsFragment.EXTRA_SHOW_TWO_FA_DIALOG, true);
            startActivity(intent);
        });

        securityPromptDialog.setNegativeButtonListener(v -> {
            securityPromptDialog.dismiss();
            if (securityPromptDialog.isChecked()) {
                viewModel.neverPrompt2Fa();
            }
        });

        if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
            securityPromptDialog.showDialog(getActivity().getSupportFragmentManager());
        }
    }

    @Override
    public void showBackupPromptDialog(boolean showNeverAgain) {
        SecurityPromptDialog securityPromptDialog = SecurityPromptDialog.newInstance(
                R.string.security_centre_backup_title,
                getString(R.string.security_centre_backup_message),
                R.drawable.vector_padlock,
                R.string.security_centre_backup_positive_button,
                true,
                showNeverAgain
        );

        securityPromptDialog.setPositiveButtonListener(v -> {
            securityPromptDialog.dismiss();
            if (securityPromptDialog.isChecked()) {
                viewModel.neverPromptBackup();
            }
            Intent intent = new Intent(getActivity(), BackupWalletActivity.class);
            startActivity(intent);
        });

        securityPromptDialog.setNegativeButtonListener(v -> {
            securityPromptDialog.dismiss();
            if (securityPromptDialog.isChecked()) {
                viewModel.neverPromptBackup();
            }
        });

        if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
            securityPromptDialog.showDialog(getActivity().getSupportFragmentManager());
        }
    }

    /**
     * Position is offset to account for first item being "All Wallets". If returned result is -1,
     * {@link piuk.blockchain.android.ui.send.SendFragment} and {@link
     * piuk.blockchain.android.ui.receive.ReceiveFragment} can safely ignore and choose the defaults
     * instead.
     */
    public int getSelectedAccountPosition() {
        int position = accountSpinner.getSelectedItemPosition();
        if (position >= accountSpinner.getCount() - 1) {
            // End of list is imported addresses, ignore
            position = 0;
        }

        return position - 1;
    }

    private void setupViews() {
        binding.noTransactionMessage.noTxMessage.setVisibility(View.GONE);

        binding.balance.setOnTouchListener((v, event) -> {
            if (balanceDisplayState == SHOW_BTC) {
                balanceDisplayState = SHOW_FIAT;
                isBTC = false;
                viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
            } else {
                balanceDisplayState = SHOW_BTC;
                isBTC = true;
                viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
            }

            transactionAdapter.onViewFormatUpdated(isBTC);
            viewModel.getPrefsUtil().setValue(PrefsUtil.KEY_BALANCE_DISPLAY_STATE, balanceDisplayState);
            return false;
        });

        accountSpinner = binding.accountsSpinner;
        viewModel.updateAccountList();

        String fiat = viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        accountsAdapter = new BalanceHeaderAdapter(
                getContext(),
                R.layout.spinner_balance_header,
                viewModel.getActiveAccountAndAddressList(),
                isBTC,
                new MonetaryUtil(viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC)),
                fiat,
                ExchangeRateFactory.getInstance().getLastPrice(fiat));

        accountsAdapter.setDropDownViewResource(R.layout.item_balance_account_dropdown);
        accountSpinner.setAdapter(accountsAdapter);
        accountSpinner.setOnTouchListener((v, event) -> event.getAction() == MotionEvent.ACTION_UP && ((MainActivity) getActivity()).getDrawerOpen());
        accountSpinner.post(() -> accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                //Refresh balance header and tx list
                viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
                binding.rvTransactions.scrollToPosition(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // No-op
            }
        }));

        String fiatString = viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        double lastPrice = ExchangeRateFactory.getInstance().getLastPrice(fiatString);

        transactionAdapter = new BalanceListAdapter(
                viewModel.getTransactionList(),
                viewModel.getPrefsUtil(),
                viewModel.getMonetaryUtil(),
                viewModel.stringUtils,
                dateUtil,
                lastPrice,
                isBTC);
        transactionAdapter.setTxListClickListener(new BalanceListAdapter.TxListClickListener() {
            @Override
            public void onTransactionClicked(int position) {
                goToTransactionDetail(position);
            }

            @Override
            public void onValueClicked(boolean isBtc) {
                isBTC = isBtc;
                viewModel.getPrefsUtil().setValue(PrefsUtil.KEY_BALANCE_DISPLAY_STATE, isBtc ? SHOW_BTC : SHOW_FIAT);
                viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBtc);
            }

            @Override
            public void onFctxClicked(String fctxId) {
                // TODO: 03/02/2017
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        binding.rvTransactions.setHasFixedSize(true);
        binding.rvTransactions.setLayoutManager(layoutManager);
        binding.rvTransactions.setAdapter(transactionAdapter);

        // drawerTitle account now that wallet has been created
        if (viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, "").length() > 0) {
            viewModel.getPayloadManager().getPayload().getHdWallet().getAccounts().get(0).setLabel(viewModel.getPrefsUtil().getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, ""));
            viewModel.getPrefsUtil().removeValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME);
            PayloadBridge.getInstance().remoteSaveThread(new PayloadBridge.PayloadSaveListener() {
                @Override
                public void onSaveSuccess() {
                    // No-op
                }

                @Override
                public void onSaveFail() {
                    ToastCustom.makeText(getActivity(), getActivity().getString(R.string.remote_save_ko), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                }
            });
            accountsAdapter.notifyDataSetChanged();
        }

        binding.swipeContainer.setProgressViewEndTarget(false, (int) ViewUtils.convertDpToPixel(72 + 20, getActivity()));
        binding.swipeContainer.setOnRefreshListener(() -> viewModel.onTransactionListRefreshed());
        binding.swipeContainer.setColorSchemeResources(
                R.color.blockchain_receive_green,
                R.color.blockchain_blue,
                R.color.blockchain_send_red);
    }

    @Override
    public boolean isBtc() {
        return isBTC;
    }

    @Override
    public int getSelectedItemPosition() {
        return accountSpinner.getSelectedItemPosition();
    }

    @Thunk
    void setToolbarOffset(int distance) {
        binding.balanceLayout.setTranslationY(-distance);
        if (distance > 1) {
            ViewUtils.setElevation(appBarLayout, ViewUtils.convertDpToPixel(5F, getActivity()));
        } else {
            ViewUtils.setElevation(appBarLayout, 0F);
        }
    }

    @Thunk
    void goToTransactionDetail(int position) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_TRANSACTION_LIST_POSITION, position);
        TransactionDetailActivity.start(getActivity(), bundle);
    }

    @Override
    public void onRefreshAccounts() {
        if (accountSpinner != null) setAccountSpinner();
        getActivity().runOnUiThread(() -> {
            if (accountsAdapter != null) accountsAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onAccountSizeChange() {
        if (accountSpinner != null) accountSpinner.setSelection(0);
    }

    @SuppressLint("ObsoleteSdkInt")
    @Override
    public void onRefreshBalanceAndTransactions() {
        // Notify adapter of change, let DiffUtil work out what needs changing
        List<Object> newTransactions = new ArrayList<>();
        ListUtil.addAllIfNotNull(newTransactions, viewModel.getTransactionList());
        transactionAdapter.onTransactionsUpdated(newTransactions);
        binding.balanceLayout.post(() -> setToolbarOffset(0));

        //Display help text to user if no transactionList on selected account/address
        if (viewModel.getTransactionList().size() > 0) {
            binding.rvTransactions.setVisibility(View.VISIBLE);
            binding.noTransactionMessage.noTxMessage.setVisibility(View.GONE);
        } else {
            binding.rvTransactions.setVisibility(View.GONE);
            binding.noTransactionMessage.noTxMessage.setVisibility(View.VISIBLE);
        }

        if (isAdded() && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //Fix for padding bug related to Android 4.1
            float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, getResources().getDisplayMetrics());
            binding.balance.setPadding((int) px, 0, 0, 0);
        }

        accountsAdapter.notifyBtcChanged(isBTC);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        viewModel.destroy();
    }

    @Override
    public void onScrollToTop() {
        if (getActivity() != null && !getActivity().isFinishing()) {
            binding.rvTransactions.smoothScrollToPosition(0);
        }
    }

    public interface Communicator {

        void resetNavigationDrawer();

    }

    abstract class CollapseActionbarScrollListener extends RecyclerView.OnScrollListener {

        private int toolbarOffset = 0;

        CollapseActionbarScrollListener() {
            // Empty Constructor
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if ((toolbarOffset < balanceBarHeight && dy > 0) || (toolbarOffset > 0 && dy < 0)) {
                toolbarOffset += dy;
            }

            clipToolbarOffset();
            onMoved(toolbarOffset);
        }

        private void clipToolbarOffset() {
            if (toolbarOffset > balanceBarHeight) {
                toolbarOffset = balanceBarHeight;
            } else if (toolbarOffset < 0) {
                toolbarOffset = 0;
            }
        }

        public abstract void onMoved(int distance);
    }
}