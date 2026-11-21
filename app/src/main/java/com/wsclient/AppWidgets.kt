package com.wsclient

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

// =============================================================
// Общие мелочи
// =============================================================

private val DANGER_COLOR = Color.rgb(235, 91, 91)

private fun Context.px(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun Context.pxf(value: Float): Float =
    value * resources.displayMetrics.density

private fun mediumFont(): Typeface =
    Typeface.create("sans-serif-medium", Typeface.NORMAL)

private fun roundFill(
    color: Int,
    radius: Float,
    strokeColor: Int = 0,
    strokeWidth: Int = 0
): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

private fun findEdit(v: View?): EditText? {
    if (v is EditText) return v

    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            findEdit(v.getChildAt(i))?.let { return it }
        }
    }

    return null
}

private fun pill(
    ctx: Context,
    label: String,
    bg: Int,
    fg: Int,
    onClick: () -> Unit
): TextView =
    TextView(ctx).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(fg)
        typeface = mediumFont()
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        background = roundFill(bg, ctx.pxf(15f))
        isClickable = true
        isFocusable = true
        Ui.pressable(this)
        setOnClickListener { onClick() }
    }

// =============================================================
// Буфер обмена
// =============================================================

object AppClipboard {
    fun copy(context: Context, label: String, text: String) {
        val manager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(label, text)

        // Ссылки с ключами не должны светиться в предпросмотре буфера.
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

        manager.setPrimaryClip(clip)
    }
}

// =============================================================
// Уведомление вместо системного Toast
// =============================================================

object AppToast {
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var hideTask: Runnable? = null
    private var pending: String? = null

    /** Показать после закрытия экрана (например, после finish()). */
    fun later(message: String) {
        pending = message
    }

    fun showPending(activity: Activity) {
        val message = pending ?: return
        pending = null
        show(activity, message)
    }

    fun show(activity: Activity, message: CharSequence, long: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return

        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        removeNow()

        val p = Customization.palette(activity)

        val bar = TextView(activity).apply {
            text = message
            textSize = 13.5f
            setTextColor(p.foreground)
            typeface = mediumFont()
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                activity.px(18),
                activity.px(13),
                activity.px(18),
                activity.px(13)
            )
            background = roundFill(
                Ui.mix(p.surface, p.foreground, 0.12f),
                activity.pxf(18f),
                Ui.withAlpha(p.foreground, 16),
                activity.px(1)
            )
            elevation = activity.pxf(10f)
            isClickable = true
            setOnClickListener { removeNow() }
        }

        val bottomInset = ViewCompat.getRootWindowInsets(host)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.bottom ?: 0

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = activity.px(92) + bottomInset
            leftMargin = activity.px(24)
            rightMargin = activity.px(24)
        }

        host.addView(bar, params)

        bar.alpha = 0f
        bar.translationY = activity.pxf(14f)
        bar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        current = bar

        val task = Runnable { fadeOut(bar) }
        hideTask = task

        val duration = if (long || message.length > 60) 3600L else 2300L
        handler.postDelayed(task, duration)
    }

    private fun fadeOut(view: View) {
        view.animate()
            .alpha(0f)
            .translationY(view.height * 0.25f)
            .setDuration(200)
            .withEndAction {
                (view.parent as? ViewGroup)?.removeView(view)

                if (current === view) current = null
            }
            .start()
    }

    private fun removeNow() {
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null

        current?.let { v ->
            v.animate().cancel()
            (v.parent as? ViewGroup)?.removeView(v)
        }

        current = null
    }
}

// =============================================================
// Диалог вместо системного AlertDialog
// =============================================================

private class MaxHeightScroll(
    context: Context,
    private val maxHeight: Int
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        )
    }
}

class AppDialog private constructor(private val dialog: Dialog) {

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    class Item(
        val label: String,
        val glyph: Glyph? = null,
        val danger: Boolean = false
    )

    /** API повторяет AlertDialog.Builder, поэтому старый код меняется минимально. */
    class Builder(private val context: Context) {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var view: View? = null
        private var items: List<Item> = emptyList()
        private var checked = -1
        private var autoDismiss = true
        private var onItem: ((AppDialog, Int) -> Unit)? = null
        private var positiveText: String? = null
        private var positiveAction: ((AppDialog, Int) -> Unit)? = null
        private var positiveDanger = false
        private var negativeText: String? = null
        private var negativeAction: ((AppDialog, Int) -> Unit)? = null

        fun setTitle(value: CharSequence?): Builder = apply { title = value }

        fun setMessage(value: CharSequence?): Builder = apply { message = value }

        fun setView(value: View?): Builder = apply { view = value }

        fun setItems(
            labels: Array<String>,
            onClick: (AppDialog, Int) -> Unit
        ): Builder = apply {
            items = labels.map { Item(it) }
            checked = -1
            autoDismiss = true
            onItem = onClick
        }

        fun setItems(
            list: List<Item>,
            onClick: (AppDialog, Int) -> Unit
        ): Builder = apply {
            items = list
            checked = -1
            autoDismiss = true
            onItem = onClick
        }

        fun setSingleChoiceItems(
            labels: Array<String>,
            checkedItem: Int,
            onClick: (AppDialog, Int) -> Unit
        ): Builder = apply {
            items = labels.map { Item(it) }
            checked = checkedItem
            autoDismiss = false
            onItem = onClick
        }

        fun setPositiveButton(
            text: String,
            onClick: ((AppDialog, Int) -> Unit)?
        ): Builder = apply {
            positiveText = text
            positiveAction = onClick
        }

        fun setNegativeButton(
            text: String,
            onClick: ((AppDialog, Int) -> Unit)?
        ): Builder = apply {
            negativeText = text
            negativeAction = onClick
        }

        /** Красная главная кнопка для опасных действий (удаление). */
        fun danger(): Builder = apply { positiveDanger = true }

        fun show(): AppDialog {
            val ctx = context
            val p = Customization.palette(ctx)
            val accent = Customization.accent(ctx)
            val active = if (p.isLight) accent else Ui.mix(accent, Color.WHITE, 0.3f)
            val inner = Ui.mix(p.surface, p.foreground, 0.08f)

            val dialog = Dialog(ctx)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val result = AppDialog(dialog)

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = roundFill(
                    p.surface,
                    ctx.pxf(26f),
                    Ui.withAlpha(p.foreground, 14),
                    ctx.px(1)
                )
                isClickable = true
            }

            title?.takeIf { it.isNotEmpty() }?.let { t ->
                card.addView(
                    TextView(ctx).apply {
                        text = t
                        textSize = 19f
                        setTextColor(p.foreground)
                        typeface = mediumFont()
                        setPadding(ctx.px(22), ctx.px(22), ctx.px(22), ctx.px(4))
                    }
                )
            }

            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }

            message?.takeIf { it.isNotEmpty() }?.let { m ->
                body.addView(
                    TextView(ctx).apply {
                        text = m
                        textSize = 14f
                        setTextColor(Ui.mix(p.foreground, p.muted, 0.4f))
                        setLineSpacing(0f, 1.1f)
                        setPadding(ctx.px(22), ctx.px(6), ctx.px(22), ctx.px(6))
                    }
                )
            }

            view?.let { v ->
                (v.parent as? ViewGroup)?.removeView(v)

                val holder = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ctx.px(22), ctx.px(10), ctx.px(22), ctx.px(4))
                }

                holder.addView(
                    v,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                body.addView(holder)
            }

            if (items.isNotEmpty()) {
                val list = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ctx.px(14), ctx.px(10), ctx.px(14), ctx.px(2))
                }

                items.forEachIndexed { index, item ->
                    val selected = index == checked

                    val textColor = when {
                        item.danger -> DANGER_COLOR
                        selected -> active
                        else -> p.foreground
                    }

                    val iconColor = if (item.danger) DANGER_COLOR else active

                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(ctx.px(14), ctx.px(14), ctx.px(14), ctx.px(14))
                        background = roundFill(
                            if (selected) Ui.withAlpha(accent, 20) else inner,
                            ctx.pxf(15f)
                        )
                        isClickable = true
                        isFocusable = true
                    }

                    Ui.pressable(row)

                    val glyph = item.glyph

                    if (glyph != null) {
                        row.addView(
                            GlyphView(ctx, glyph, iconColor),
                            LinearLayout.LayoutParams(ctx.px(22), ctx.px(22))
                        )
                    }

                    row.addView(
                        TextView(ctx).apply {
                            text = item.label
                            textSize = 15f
                            setTextColor(textColor)
                            typeface = mediumFont()
                        },
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            leftMargin = if (glyph != null) ctx.px(14) else 0
                        }
                    )

                    if (selected) {
                        row.addView(
                            GlyphView(ctx, Glyph.CHECK, active),
                            LinearLayout.LayoutParams(ctx.px(20), ctx.px(20))
                        )
                    }

                    row.setOnClickListener {
                        if (autoDismiss) result.dismiss()
                        onItem?.invoke(result, index)
                    }

                    list.addView(
                        row,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = ctx.px(8) }
                    )
                }

                body.addView(list)
            }

            val maxHeight = (ctx.resources.displayMetrics.heightPixels * 0.6f).toInt()

            card.addView(
                MaxHeightScroll(ctx, maxHeight).apply {
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    addView(body)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            if (positiveText != null || negativeText != null) {
                val bar = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(ctx.px(14), ctx.px(12), ctx.px(14), ctx.px(14))
                }

                negativeText?.let { label ->
                    bar.addView(
                        pill(ctx, label, inner, p.foreground) {
                            result.dismiss()
                            negativeAction?.invoke(result, 0)
                        },
                        LinearLayout.LayoutParams(0, ctx.px(48), 1f)
                    )
                }

                positiveText?.let { label ->
                    val bg = if (positiveDanger) DANGER_COLOR else accent

                    bar.addView(
                        pill(ctx, label, bg, Color.WHITE) {
                            result.dismiss()
                            positiveAction?.invoke(result, 0)
                        },
                        LinearLayout.LayoutParams(0, ctx.px(48), 1f).apply {
                            if (negativeText != null) leftMargin = ctx.px(10)
                        }
                    )
                }

                card.addView(bar)
            } else {
                card.addView(
                    View(ctx),
                    LinearLayout.LayoutParams(1, ctx.px(12))
                )
            }

            val sheet = items.isNotEmpty()

            val wrapper = FrameLayout(ctx).apply {
                setPadding(ctx.px(16), ctx.px(16), ctx.px(16), ctx.px(16))
                setOnClickListener { result.dismiss() }
            }

            wrapper.addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (sheet) Gravity.BOTTOM else Gravity.CENTER
                )
            )

            ViewCompat.setOnApplyWindowInsetsListener(wrapper) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.ime()
                )

                val pad = ctx.px(16)

                v.setPadding(
                    pad + bars.left,
                    pad + bars.top,
                    pad + bars.right,
                    pad + bars.bottom
                )

                insets
            }

            val hasInput = findEdit(view) != null

            dialog.setContentView(wrapper)
            dialog.setCanceledOnTouchOutside(true)

            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.6f)
                setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        if (hasInput) {
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        } else {
                            0
                        }
                )
            }

            dialog.show()

            card.alpha = 0f
            card.translationY = ctx.pxf(if (sheet) 40f else 18f)
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()

            findEdit(view)?.requestFocus()

            return result
        }
    }
}

// =============================================================
// Переключатель
// =============================================================

class AppSwitch(context: Context) : View(context) {
    private val p = Customization.palette(context)
    private val accent = Customization.accent(context)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var checked = false
    private var fraction = 0f
    private var anim: ValueAnimator? = null
    private var listener: ((AppSwitch, Boolean) -> Unit)? = null

    /** Установка из кода: без анимации и без вызова слушателя. */
    var isChecked: Boolean
        get() = checked
        set(value) {
            anim?.cancel()
            checked = value
            fraction = if (value) 1f else 0f
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
    }

    fun setOnCheckedChangeListener(l: ((AppSwitch, Boolean) -> Unit)?) {
        listener = l
    }

    fun toggle() {
        checked = !checked
        animateTo(if (checked) 1f else 0f)
        listener?.invoke(this, checked)
    }

    private fun animateTo(target: Float) {
        anim?.cancel()

        anim = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 170
            addUpdateListener {
                fraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.px(50), context.px(30))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density

        val off = Ui.mix(p.surface, p.foreground, 0.2f)
        trackPaint.color = Ui.mix(off, accent, fraction)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, trackPaint)

        val r = h / 2f - 3f * d
        val x = (h / 2f) + (w - h) * fraction

        thumbPaint.color = Ui.mix(Ui.mix(p.muted, Color.WHITE, 0.55f), Color.WHITE, fraction)
        canvas.drawCircle(x, h / 2f, r, thumbPaint)
    }
}

// =============================================================
// Чекбокс (одна или две строки текста)
// =============================================================

class AppCheckBox(
    context: Context,
    label: CharSequence = "",
    note: CharSequence? = null
) : LinearLayout(context) {

    private val p = Customization.palette(context)
    private val accent = Customization.accent(context)

    private val box = Box(context)
    private val titleView = TextView(context)
    private val noteView = TextView(context)

    private var checked = false
    private var listener: ((AppCheckBox, Boolean) -> Unit)? = null

    var text: CharSequence
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    var subtitle: CharSequence?
        get() = noteView.text
        set(value) {
            noteView.text = value ?: ""
            noteView.visibility =
                if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

    /** Установка из кода не вызывает слушатель. */
    var isChecked: Boolean
        get() = checked
        set(value) {
            checked = value
            box.invalidate()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.px(2), context.px(9), context.px(2), context.px(9))

        titleView.textSize = 14f
        titleView.setTextColor(p.foreground)

        noteView.textSize = 11f
        noteView.setTextColor(p.muted)

        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView)
            addView(noteView)
        }

        addView(box, LinearLayout.LayoutParams(context.px(22), context.px(22)))

        addView(
            column,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { leftMargin = context.px(12) }
        )

        text = label
        subtitle = note

        isClickable = true
        isFocusable = true
        Ui.pressable(this)

        setOnClickListener {
            checked = !checked
            box.invalidate()
            listener?.invoke(this, checked)
        }
    }

    fun setOnCheckedChangeListener(l: ((AppCheckBox, Boolean) -> Unit)?) {
        listener = l
    }

    private inner class Box(ctx: Context) : View(ctx) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ctx.pxf(2f)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val inset = ring.strokeWidth / 2f
            val rect = RectF(inset, inset, w - inset, h - inset)
            val radius = resources.displayMetrics.density * 7f

            if (checked) {
                fill.color = accent
                canvas.drawRoundRect(rect, radius, radius, fill)
                Glyphs.draw(canvas, Glyph.CHECK, w, Color.WHITE, glyph)
            } else {
                ring.color = p.muted
                canvas.drawRoundRect(rect, radius, radius, ring)
            }
        }
    }
}

// =============================================================
// Слайдер
// =============================================================

class AppSlider(
    context: Context,
    private val min: Int,
    private val max: Int,
    initial: Int,
    private val onChanged: (Int) -> Unit
) : View(context) {

    private val p = Customization.palette(context)
    private val accent = Customization.accent(context)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var current = initial.coerceIn(min, max)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            context.px(38)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val pad = 14f * d
        val cy = height / 2f
        val left = pad
        val right = width - pad

        val fraction =
            if (max == min) 0f else (current - min).toFloat() / (max - min)

        val x = left + (right - left) * fraction
        val th = 6f * d

        trackPaint.color = Ui.mix(p.surface, p.foreground, 0.18f)
        canvas.drawRoundRect(
            RectF(left, cy - th / 2f, right, cy + th / 2f),
            th / 2f, th / 2f, trackPaint
        )

        trackPaint.color = accent
        canvas.drawRoundRect(
            RectF(left, cy - th / 2f, x, cy + th / 2f),
            th / 2f, th / 2f, trackPaint
        )

        thumbPaint.color = accent
        canvas.drawCircle(x, cy, 11f * d, thumbPaint)

        thumbPaint.color = Color.WHITE
        canvas.drawCircle(x, cy, 4.5f * d, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x)
            }

            MotionEvent.ACTION_MOVE -> update(event.x)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }

        return true
    }

    private fun update(x: Float) {
        val pad = 14f * resources.displayMetrics.density
        val span = (width - 2 * pad).coerceAtLeast(1f)
        val fraction = ((x - pad) / span).coerceIn(0f, 1f)
        val value = (min + fraction * (max - min)).roundToInt()

        if (value != current) {
            current = value
            invalidate()
            onChanged(value)
        }
    }
}

// =============================================================
// Поле ввода
// =============================================================

object AppInput {
    fun create(
        context: Context,
        hint: String,
        text: String = "",
        multiline: Boolean = false,
        uri: Boolean = false
    ): EditText {
        val p = Customization.palette(context)
        val accent = Customization.accent(context)

        return EditText(context).apply {
            this.hint = hint
            setText(text)
            textSize = 14f
            setTextColor(p.foreground)
            setHintTextColor(Ui.withAlpha(p.muted, 80))

            inputType = when {
                multiline -> InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                uri -> InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_URI
                else -> InputType.TYPE_CLASS_TEXT
            }

            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            } else {
                setSingleLine(true)
            }

            background = roundFill(
                Ui.mix(p.surface, p.foreground, 0.08f),
                context.pxf(14f)
            )

            setPadding(
                context.px(14),
                context.px(12),
                context.px(14),
                context.px(12)
            )

            highlightColor = Ui.withAlpha(accent, 40)

            if (Build.VERSION.SDK_INT >= 29) {
                setTextCursorDrawable(
                    GradientDrawable().apply {
                        setColor(accent)
                        setSize(context.px(2), context.px(20))
                    }
                )
            }
        }
    }
}
