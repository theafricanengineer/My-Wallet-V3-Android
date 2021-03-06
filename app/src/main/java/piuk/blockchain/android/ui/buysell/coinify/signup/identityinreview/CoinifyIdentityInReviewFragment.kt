package piuk.blockchain.android.ui.buysell.coinify.signup.identityinreview

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.dialog_fragment_coinify_id_in_review.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.buysell.coinify.signup.CoinifyFlowListener
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import com.blockchain.ui.dialog.MaterialProgressDialog
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.androidcoreui.utils.extensions.invisibleIf
import piuk.blockchain.androidcoreui.utils.extensions.visible
import kotlinx.android.synthetic.main.dialog_fragment_coinify_id_in_review.text_view_finish_verification as finishKyc

class CoinifyIdentityInReviewFragment :
    BaseFragment<CoinifyIdentityInReviewView, CoinifyIdentityInReviewPresenter>(),
    CoinifyIdentityInReviewView {

    private val presenter: CoinifyIdentityInReviewPresenter by inject()
    private var progressDialog: MaterialProgressDialog? = null
    private var signUpListener: CoinifyFlowListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.dialog_fragment_coinify_id_in_review)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonContinue.setOnClickListener { closeAndRestartBuySell() }

        onViewReady()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CoinifyFlowListener) {
            signUpListener = context
        } else {
            throw RuntimeException("$context must implement CoinifyFlowListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        signUpListener = null
    }

    private fun closeAndRestartBuySell() {
        onFinish()
    }

    override fun onShowLoading() {
        displayProgressDialog()
        updateStatusVisibility(false)
    }

    override fun dismissLoading() {
        dismissProgressDialog()
    }

    override fun onShowCompleted() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_completed)
        )
        updateStatusVisibility(true)
    }

    override fun onShowReviewing() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_reviewing)
        )
        updateStatusVisibility(true)
    }

    override fun onShowPending() {
        textviewReviewTitle.setText(R.string.buy_sell_review_finish_verification_title)
        textviewReviewStatus.apply {
            text = getString(
                R.string.buy_sell_review_status,
                getString(R.string.buy_sell_review_status_in_pending)
            )
        }

        textviewReviewMessage.setText(R.string.buy_sell_review_status_pending_message)
        updateStatusVisibility(true)
        finishKyc.apply {
            visible()
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { signUpListener?.requestContinueVerification() }
        }
    }

    override fun onShowRejected() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_rejected)
        )
        textviewReviewTitle.setText(R.string.buy_sell_review_failed)
        textviewReviewMessage.text = getString(R.string.buy_sell_review_status_failed)
        updateStatusVisibility(true)
    }

    override fun onShowExpired() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_expired)
        )
        textviewReviewTitle.setText(R.string.buy_sell_review_failed)
        textviewReviewMessage.text = getString(R.string.buy_sell_review_status_failed)
        updateStatusVisibility(true)
    }

    override fun onShowFailed() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_failed)
        )
        textviewReviewTitle.setText(R.string.buy_sell_review_failed)
        textviewReviewMessage.text = getString(R.string.buy_sell_review_status_failed)
        updateStatusVisibility(true)
    }

    override fun onShowDocumentsRequested() {
        textviewReviewStatus.text = getString(
            R.string.buy_sell_review_status,
            getString(R.string.buy_sell_review_status_in_docs_requested)
        )
        updateStatusVisibility(true)
    }

    private fun updateStatusVisibility(visible: Boolean) {
        textviewReviewMessage.invisibleIf { !visible }
        textviewReviewStatus.invisibleIf { !visible }
        textviewReviewTitle.invisibleIf { !visible }
    }

    override fun onFinish() {
        signUpListener?.requestFinish()
    }

    override fun createPresenter() = presenter

    override fun getMvpView() = this

    private fun displayProgressDialog() {
        if (activity?.isFinishing == false) {
            progressDialog = MaterialProgressDialog(requireContext()).apply {
                setMessage(getString(R.string.buy_sell_review_status_fetching))
                setCancelable(false)
                show()
            }
        }
    }

    private fun dismissProgressDialog() {
        if (progressDialog?.isShowing == true) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
    }

    companion object {

        internal fun newInstance(): CoinifyIdentityInReviewFragment =
            CoinifyIdentityInReviewFragment()
    }
}