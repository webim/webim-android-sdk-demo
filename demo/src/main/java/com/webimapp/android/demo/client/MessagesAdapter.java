package com.webimapp.android.demo.client;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.webimapp.android.demo.client.items.ViewType;
import com.webimapp.android.sdk.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;

import static com.webimapp.android.sdk.Message.*;
import static com.webimapp.android.sdk.Message.Type.*;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageHolder> {
    private static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

    private final List<Message> messageList;
    private final WebimChatFragment webimChatFragment;

    MessagesAdapter(WebimChatFragment webimChatFragment) {
        this.webimChatFragment = webimChatFragment;
        this.messageList = new ArrayList<>();
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewType vt = ViewType.values()[viewType];
        View view = LayoutInflater.from(webimChatFragment.getContext()).inflate(getLayout(vt),
                parent, false);
        switch (vt) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return new ReceivedMessageHolder(view);
            case VISITOR:
            case FILE_FROM_VISITOR:
                return new SentMessageHolder(view);
            case INFO:
            case INFO_OPERATOR_BUSY:
                return new MessageHolder(view);
            default:
                return null;
        }


    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        Message prev = position < messageList.size() - 1
                ? messageList.get(messageList.size() - position - 2)
                : null;

        if (prev != null) {
            if ((message.getType().equals(OPERATOR)
                    || message.getType().equals(FILE_FROM_OPERATOR))
                    && (prev.getType().equals(OPERATOR)
                    || prev.getType().equals(FILE_FROM_OPERATOR))
                    && prev.getOperatorId().equals(message.getOperatorId())) {
                ((ReceivedMessageHolder) holder).showSenderInfo = false;
            }
        }

        boolean showDate = false;
        if (prev == null
                || (message.getTime() / MILLIS_IN_DAY != prev.getTime() / MILLIS_IN_DAY)) {
            showDate = true;
        }

        holder.bind(message, showDate);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        if (message.getSendStatus() == SendStatus.SENDING) {
            return ViewType.VISITOR.ordinal();
        }
        switch (message.getType()) {
            case CONTACT_REQUEST:
            case OPERATOR:
                return ViewType.OPERATOR.ordinal();
            case VISITOR:
                return ViewType.VISITOR.ordinal();
            case INFO:
                return ViewType.INFO.ordinal();
            case OPERATOR_BUSY:
                return ViewType.INFO_OPERATOR_BUSY.ordinal();
            case FILE_FROM_VISITOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? ViewType.VISITOR.ordinal()
                        : ViewType.FILE_FROM_VISITOR.ordinal();
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? ViewType.OPERATOR.ordinal()
                        : ViewType.FILE_FROM_OPERATOR.ordinal();
            default:
                return ViewType.INFO.ordinal();
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private int getLayout(ViewType viewType) {
        switch (viewType) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return R.layout.item_message_received;
            case VISITOR:
            case FILE_FROM_VISITOR:
                return R.layout.item_message_sent;
            case INFO_OPERATOR_BUSY:
                return R.layout.item_op_busy_message;
            case INFO:
                return R.layout.item_info_message;
            case KEYBOARD:
            case KEYBOARD_RESPONCE:
                return R.layout.item_info_message;
            default:
                return R.layout.item_info_message;
        }
    }

    public boolean add(Message message) {
        return messageList.add(message);
    }

    public void add(int i, Message message) {
        messageList.add(i, message);
    }

    public boolean addAll(int i, Collection<? extends Message> collection) {
        return messageList.addAll(i, collection);
    }

    public Message set(int i, Message message) {
        return messageList.set(i, message);
    }

    public Message remove(int i) {
        return messageList.remove(i);
    }

    public void clear() {
        messageList.clear();
    }

    public int indexOf(Message message) {
        return messageList.indexOf(message);
    }

    public int lastIndexOf(Message message) {
        return messageList.lastIndexOf(message);
    }

    private void showMessage(String message, View view) {
        Toast.makeText(view.getContext().getApplicationContext(),
                message, Toast.LENGTH_SHORT).show();
    }

    class MessageHolder extends RecyclerView.ViewHolder {
        Message message;
        TextView messageText;
        TextView messageDate;
        TextView messageTime;
        ImageView messageTick;
        RelativeLayout quoteLayout;
        LinearLayout quote_body;
        TextView quoteSenderName;
        TextView quoteText;
        LinearLayout messageBody;


        MessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            messageDate = itemView.findViewById(R.id.text_message_date);
            messageTime = itemView.findViewById(R.id.text_message_time);
            messageTick = itemView.findViewById(R.id.tick);
            quoteLayout = itemView.findViewById(R.id.quote_message);
            quote_body = itemView.findViewById(R.id.quote_body);
            quoteSenderName = itemView.findViewById(R.id.quote_sender_name);
            quoteText = itemView.findViewById(R.id.quote_text);
            messageBody = itemView.findViewById(R.id.message_body);
        }

        public void bind(final Message message, boolean showDate) {
            this.message = message;

            messageBody.setVisibility(View.VISIBLE);
            if (messageText != null) {
                messageText.setText(message.getText());
                messageText.setVisibility(View.VISIBLE);
            }
            if (messageDate != null) {
                if (showDate) {
                    messageDate
                            .setText(DateFormat.getDateFormat(webimChatFragment.getContext())
                                    .format(message.getTime()));
                    messageDate.setVisibility(View.VISIBLE);
                } else {
                    messageDate.setVisibility(View.GONE);
                }
            }
            if (messageTime != null) {
                messageTime
                        .setText(DateFormat.getTimeFormat(webimChatFragment.getContext())
                                .format(message.getTime()));
                messageText.setVisibility(View.VISIBLE);
            }

            if (messageTick != null) {
                if (message.isReadByOperator()) {
                    messageTick.setImageResource(R.drawable.ic_double_tick);
                } else {
                    messageTick.setImageResource(R.drawable.ic_tick);
                }
                messageTick.setVisibility(message.getSendStatus() == SendStatus.SENT
                        ? View.VISIBLE
                        : View.GONE);
            }

            if (quoteLayout != null) {
                if (message.getQuote() != null) {
                    quoteLayout.setVisibility(View.VISIBLE);
                    quoteSenderName.setVisibility(View.VISIBLE);
                    quoteText.setVisibility(View.VISIBLE);
                    String textQuoteSenderName = "";
                    String textQuote = "";
                    if (message.getQuote().getState() == Quote.State.PENDING) {
                        textQuote = (message.getType() == OPERATOR)
                                ? webimChatFragment.getResources().getString(R.string.quote_is_pending)
                                : message.getQuote().getMessageText();
                    }
                    if (message.getQuote().getState() == Quote.State.FILLED) {
                        if (message.getQuote().getMessageType() == FILE_FROM_OPERATOR ||
                                message.getQuote().getMessageType() == FILE_FROM_VISITOR) {
                            textQuote =
                                    message.getQuote().getMessageAttachment().getFileName();
                        } else {
                            textQuote = message.getQuote().getMessageText();
                        }
                        textQuoteSenderName = message.getQuote().getSenderName();
                    }
                    if (message.getQuote().getState() == Quote.State.NOT_FOUND) {
                        quoteSenderName.setVisibility(View.GONE);
                        textQuote = webimChatFragment.getResources().getString(R.string.quote_is_not_found);
                    }
                    quoteSenderName.setText(textQuoteSenderName);
                    quoteText.setText(textQuote);
                } else {
                    quoteLayout.setVisibility(View.GONE);
                    quoteSenderName.setVisibility(View.GONE);
                    quoteText.setVisibility(View.GONE);
                }
            }
        }
    }

    class FileMessageHolder extends MessageHolder {

        private final double PORTRAIT_ORIENTATION = 0.6;
        private final double LANDSCAPE_ORIENTATION = 0.8;

        ImageView thumbView, quoteView;
        ConstraintLayout layoutQuotedView;
        TextView senderNameForImage;

        FileMessageHolder(View itemView) {
            super(itemView);
            thumbView = itemView.findViewById(R.id.attached_image);
            quoteView = itemView.findViewById(R.id.quoted_image);
            layoutQuotedView = itemView.findViewById(R.id.const_quoted_image);
            senderNameForImage = itemView.findViewById(R.id.sender_name_for_image);
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            thumbView.setVisibility(View.GONE);
            layoutQuotedView.setVisibility(View.GONE);

            Attachment attachment = message.getAttachment();
            if (attachment == null) {
                if (thumbView != null && message.getType().equals(FILE_FROM_VISITOR)) {
                    thumbView.setVisibility(View.GONE);
                    messageText.setText(R.string.uploading_file);
                    messageText.setVisibility(View.VISIBLE);
                }
            } else {
                if (attachment.getImageInfo() == null) {
                    getMessageForEmptyImage(attachment, thumbView, messageText);
                    return;
                }

                if (thumbView != null) {
                    setViewSize(thumbView, getThumbSize(attachment.getImageInfo()));
                    addImageInView(attachment, thumbView, messageText);
                    messageBody.setVisibility(View.GONE);
                }
            }

            if (message.getQuote() != null && message.getQuote() != null) {
                attachment = message.getQuote().getMessageAttachment();
                if (attachment != null) {
                    if (attachment.getImageInfo() == null) {
                        getMessageForEmptyImage(attachment, quoteView, quoteText);
                        return;
                    }

                    if (quoteView != null) {
                        addImageInView(attachment, quoteView, quoteText);
                        layoutQuotedView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        private void getMessageForEmptyImage(
                Attachment attachment,
                ImageView imageView,
                TextView textView) {
            if (imageView != null) {
                imageView.setVisibility(View.GONE);
            }
            textView.setText(
                    Html.fromHtml(webimChatFragment.getResources().getString(R.string.file_send)
                    + "<a href=\"" + attachment.getUrl() + "\">" + attachment.getFileName() + "</a>"));
            textView.setVisibility(View.VISIBLE);
        }

        private void addImageInView(
                Attachment attachment,
                ImageView imageView,
                TextView textView) {
            String imageUrl = attachment.getImageInfo().getThumbUrl();
            final Uri fileUrl = Uri.parse(attachment.getUrl());
            RoundedCornersTransformation.CornerType cornerType;
            if (message.getType().equals(FILE_FROM_OPERATOR)) {
                cornerType = RoundedCornersTransformation.CornerType.OTHER_BOTTOM_LEFT;
            } else {
                cornerType = RoundedCornersTransformation.CornerType.OTHER_BOTTOM_RIGHT;
            }
            Glide.with(webimChatFragment)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .bitmapTransform(new RoundedCornersTransformation(
                            webimChatFragment.getContext(), 12, 0, cornerType))
                    .into(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent openImgIntent = new Intent(view.getContext(), ImageActivity.class);
                    openImgIntent.setData(fileUrl);
                    Context context = view.getContext();
                    if (context instanceof ContextWrapper) {
                        context = ((ContextWrapper)context).getBaseContext();
                    }
                    Activity activity = (Activity) context;
                    activity.startActivity(openImgIntent);
                    activity.overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
                }
            });
            textView.setText(attachment.getFileName());
            textView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }

        private Size getThumbSize(ImageInfo imageInfo) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            webimChatFragment.getActivity().getWindowManager().
                    getDefaultDisplay().getMetrics(displayMetrics);
            int imageWidth =  Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
            double width = imageInfo.getWidth();
            double height = imageInfo.getHeight();
            double sideImageRatio = width / height;
            width = imageWidth * (height > width ? PORTRAIT_ORIENTATION : LANDSCAPE_ORIENTATION);
            height = width / sideImageRatio;
            return new Size((int) width, (int) height);
        }

        private void setViewSize(ImageView view, Size size) {
            if (view.getParent() instanceof FrameLayout) {
                view.setLayoutParams(new FrameLayout.LayoutParams(size.getWidth(),
                        size.getHeight(), Gravity.END));
            }
            else {
                view.setLayoutParams(new LinearLayout.LayoutParams(size.getWidth(),
                        size.getHeight(), Gravity.END));
            }
        }

        private class Size {
            private final int width;
            private final int height;

            private Size(int width, int height) {
                this.width = width;
                this.height = height;
            }

            public int getWidth() {
                return width;
            }

            public int getHeight() {
                return height;
            }
        }
    }

    private class SentMessageHolder extends FileMessageHolder {
        ProgressBar sendingProgress;

        private float LEVEL_SHADING = 0.6f;

        SentMessageHolder(final View itemView) {
            super(itemView);

            sendingProgress = itemView.findViewById(R.id.sending_msg);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    changeBackgroundDrawable(
                            view,
                            messageBody,
                            R.drawable.background_send_message,
                            R.color.sendingMsgSelect);
                    final Dialog dialog = new Dialog(view.getContext());
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_message_menu);
                    WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                    layoutParams.dimAmount = LEVEL_SHADING;
                    dialog.getWindow().setAttributes(layoutParams);
                    RelativeLayout replyLayout = dialog.findViewById(R.id.relLayoutReply);
                    RelativeLayout copyLayout = dialog.findViewById(R.id.relLayoutCopy);
                    RelativeLayout editLayout = dialog.findViewById(R.id.relLayoutEdit);
                    RelativeLayout deleteLayout = dialog.findViewById(R.id.relLayoutDelete);
                    if (message.canBeReplied()) {
                        replyLayout.setVisibility(View.VISIBLE);
                        replyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onReplyMessageAction(message, getAdapterPosition());
                                dialog.dismiss();
                            }
                        });
                    } else {
                        replyLayout.setVisibility(View.GONE);
                    }
                    copyLayout.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ClipData clip =
                                    ClipData.newPlainText("message", message.getText());
                            ClipboardManager clipboard =
                                    (ClipboardManager) view.getContext()
                                            .getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                                showMessage(view.getResources()
                                        .getString(R.string.copied_message), view);
                            } else {
                                showMessage(view.getResources()
                                        .getString(R.string.copy_failed), view);
                            }
                            dialog.dismiss();
                        }
                    });
                    if (message.canBeEdited()) {
                        editLayout.setVisibility(View.VISIBLE);
                        editLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onEditAction(message);
                                dialog.dismiss();
                            }
                        });
                        deleteLayout.setVisibility(View.VISIBLE);
                        deleteLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onDeleteMessageAction(message);
                                dialog.dismiss();
                            }
                        });
                    } else {
                        editLayout.setVisibility(View.GONE);
                        deleteLayout.setVisibility(View.GONE);
                    }
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            changeBackgroundDrawable(
                                    view,
                                    messageBody,
                                    R.drawable.background_send_message,
                                    R.color.sendingMsgBubble);
                        }
                    });
                    dialog.show();

                }
            });
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            boolean sending = message.getSendStatus() == SendStatus.SENDING;
            if (messageTime != null) {
                messageTime.setVisibility(sending ? View.GONE : View.VISIBLE);
            }
            if (sendingProgress != null) {
                sendingProgress.setVisibility(sending ? View.VISIBLE : View.GONE);
            }
        }
    }

    private class ReceivedMessageHolder extends FileMessageHolder {
        boolean showSenderInfo = true;
        TextView timeText, nameText, nameTextForImage;
        ImageView profileImage;

        private float LEVEL_SHADING = 0.6f;

        ReceivedMessageHolder(final View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.text_message_time);
            nameText = itemView.findViewById(R.id.sender_name);
            nameTextForImage = itemView.findViewById(R.id.sender_name_for_image);
            profileImage = itemView.findViewById(R.id.sender_photo);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    changeBackgroundDrawable(
                            view,
                            messageBody,
                            R.drawable.background_received_message,
                            R.color.receivedMsgSelect);
                    final Dialog dialog = new Dialog(view.getContext());
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_message_menu);
                    WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                    layoutParams.dimAmount = LEVEL_SHADING;
                    dialog.getWindow().setAttributes(layoutParams);
                    RelativeLayout replyLayout = dialog.findViewById(R.id.relLayoutReply);
                    RelativeLayout copyLayout = dialog.findViewById(R.id.relLayoutCopy);
                    RelativeLayout editLayout = dialog.findViewById(R.id.relLayoutEdit);
                    RelativeLayout deleteLayout = dialog.findViewById(R.id.relLayoutDelete);
                    if (message.canBeReplied()) {
                        replyLayout.setVisibility(View.VISIBLE);
                        replyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onReplyMessageAction(message, getAdapterPosition());
                                dialog.dismiss();
                            }
                        });
                    } else {
                        replyLayout.setVisibility(View.GONE);
                    }
                    copyLayout.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ClipData clip =
                                    ClipData.newPlainText("message", message.getText());
                            ClipboardManager clipboard =
                                    (ClipboardManager) view.getContext()
                                            .getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                                showMessage(view.getResources()
                                        .getString(R.string.copied_message), view);
                            } else {
                                showMessage(view.getResources()
                                        .getString(R.string.copy_failed), view);
                            }
                            dialog.dismiss();
                        }
                    });
                    editLayout.setVisibility(View.GONE);
                    deleteLayout.setVisibility(View.GONE);
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            changeBackgroundDrawable(
                                    view,
                                    messageBody,
                                    R.drawable.background_received_message,
                                    R.color.receivedMsgBubble);
                        }
                    });
                    dialog.show();
                }
            });
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);
            timeText.setText(DateFormat.getTimeFormat(webimChatFragment.getContext())
                    .format(message.getTime()));
            if (showSenderInfo) {
                if (message.getType().equals(FILE_FROM_OPERATOR)) {
                    nameTextForImage.setVisibility(View.VISIBLE);
                    nameTextForImage.setText(message.getSenderName());
                } else {
                    nameTextForImage.setVisibility(View.GONE);
                    nameText.setVisibility(View.VISIBLE);
                    nameText.setText(message.getSenderName());
                }

                String avatarUrl = message.getSenderAvatarUrl();
                profileImage.setVisibility(View.VISIBLE);
                if (avatarUrl != null) {
                    if (!avatarUrl.equals(profileImage.getTag(R.id.avatarUrl))) {
                        Glide.with(webimChatFragment).load(avatarUrl).into(profileImage);
                        profileImage.setTag(R.id.avatarUrl, avatarUrl);
                    }
                } else {
                    profileImage.setImageDrawable(
                            webimChatFragment.getResources()
                                    .getDrawable(R.drawable.default_operator_avatar));
                    profileImage.setVisibility(View.VISIBLE);
                }
            } else {
                nameText.setVisibility(View.GONE);
                nameTextForImage.setVisibility(View.GONE);
                profileImage.setVisibility(View.GONE);
                showSenderInfo = true;
            }
        }
    }

    private void changeBackgroundDrawable(
            View view,
            LinearLayout messageBody,
            int drawableResource,
            int colorForSelect) {
        Drawable unwrappedDrawable =
                AppCompatResources.getDrawable(view.getContext(), drawableResource);
        DrawableCompat.setTint(
                DrawableCompat.wrap(unwrappedDrawable),
                view.getResources().getColor(colorForSelect));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            messageBody.setBackground(
                    view.getResources().getDrawable(drawableResource));
        } else {
            messageBody.setBackgroundDrawable(
                    view.getResources().getDrawable(drawableResource));
        }
    }
}