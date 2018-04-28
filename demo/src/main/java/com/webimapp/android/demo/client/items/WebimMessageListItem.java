package com.webimapp.android.demo.client.items;


import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.webimapp.android.demo.client.ImageActivity;
import com.webimapp.android.demo.client.MessagesAdapter;
import com.webimapp.android.demo.client.R;
import com.webimapp.android.sdk.Message;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;

public class WebimMessageListItem extends ListItem {
    private static final int THUMB_SIZE = 300; // Should be the same as in the server configuration

    private static final long MILLIS_IN_MINUTE = 1000 * 60;

    private static final int NUMBER_OF_MS_IN_DAY = 1000 * 60 * 60 * 24;

    private final @NonNull Message message;
    private final ViewType viewType;
    private boolean headerVisible;

    public WebimMessageListItem(@NonNull Message message) {
        this.message = message;
        this.viewType = computeItemType(message);
        this.headerVisible = true;
    }

    @Override
    public ViewType getViewType() {
        return viewType;
    }

    private static ViewType computeItemType(Message message) {
        if (message.getSendStatus() == Message.SendStatus.SENDING) {
            return ViewType.VISITOR;
        }
        switch (message.getType()) {
            case OPERATOR:
                return ViewType.OPERATOR;
            case VISITOR:
                return ViewType.VISITOR;
            case INFO:
                return ViewType.INFO;
            case OPERATOR_BUSY:
                return ViewType.INFO_OPERATOR_BUSY;
            case FILE_FROM_VISITOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? ViewType.VISITOR
                        : ViewType.FILE_FROM_VISITOR;
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? ViewType.OPERATOR
                        : ViewType.FILE_FROM_OPERATOR;
            default:
                return ViewType.INFO;
        }
    }

    @NonNull
    public Message getMessage() {
        return message;
    }

    @Override
    public View getView(final MessagesAdapter adapter,
                        @Nullable View convertView,
                        ViewGroup parent,
                        @Nullable ListItem prev) {
        int layoutId = getLayout();
        boolean newHeaderVisible = true;
        if (prev != null) {
            newHeaderVisible = !(prev.getViewType() == viewType
                    && message.getTime() / MILLIS_IN_MINUTE
                    == ((WebimMessageListItem) prev).getMessage().getTime() / MILLIS_IN_MINUTE);
        }
        View view = convertView;
        if (convertView != null) {
            Object tag = convertView.getTag(R.id.webimMessage);
            if (tag != null) {
                if (tag == message && newHeaderVisible == headerVisible) {
                    return convertView;
                }
                Message.Type type = ((Message) tag).getType();
                if (type == Message.Type.FILE_FROM_OPERATOR
                        || type == Message.Type.FILE_FROM_VISITOR) {
                    view = null;
                }
            }
        }
        headerVisible = newHeaderVisible;
        if (view == null) {
            view = adapter.getInflater().inflate(layoutId, parent, false);
        }
        view.setClickable(true);
        return message.getSendStatus() == Message.SendStatus.SENDING
                ? getSendingMessageView(adapter, view)
                : getRealMessageView(adapter, view, prev);
    }

    public View getSendingMessageView(final MessagesAdapter adapter, @NonNull View view) {
        if (message.getType() == Message.Type.FILE_FROM_OPERATOR
                || message.getType() == Message.Type.FILE_FROM_VISITOR) {
            return getSendingFileMessageView(adapter, view);
        }
        else {
            return getSendingTextMessageView(adapter, view);
        }
    }

    public View getSendingTextMessageView(final MessagesAdapter adapter, @NonNull View view) {
        TextView messageTextView = (TextView) view.findViewById(R.id.textViewMessage);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(message.getText());
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if (sendingProgress != null) {
            sendingProgress.setVisibility(View.VISIBLE);
        }
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if (messageTime != null) {
            messageTime.setVisibility(View.GONE);
        }
        final ImageView thumb = (ImageView) view.findViewById(R.id.fileThumb);
        if (thumb != null) {
            thumb.setVisibility(View.GONE);
        }
        return view;
    }

    public View getSendingFileMessageView(MessagesAdapter adapter, @NonNull View view) {
        TextView messageTextView = (TextView) view.findViewById(R.id.textViewMessage);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(R.string.file_upload_message_text);
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if (sendingProgress != null) {
            sendingProgress.setVisibility(View.VISIBLE);
        }
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if (messageTime != null) {
            messageTime.setVisibility(View.GONE);
        }
        final ImageView thumb = (ImageView) view.findViewById(R.id.fileThumb);
        if (thumb != null) {
            thumb.setVisibility(View.GONE);
        }
        return view;
    }

    public View getRealMessageView(final MessagesAdapter adapter,
                                   @NonNull final View view,
                                   @Nullable ListItem prev) {
        view.setTag(R.id.webimMessage, message);
        TextView date = (TextView) view.findViewById(R.id.dateView);
        if (date != null) {
            if (prev == null
                    || (message.getTime() / NUMBER_OF_MS_IN_DAY
                    != ((WebimMessageListItem) prev).getMessage().getTime() / NUMBER_OF_MS_IN_DAY)) {
                date.setText(adapter.getLongDateFormat().format(message.getTime()));
                date.setVisibility(View.VISIBLE);
            } else {
                date.setVisibility(View.GONE);
            }
        }

        TextView messageTextView = (TextView) view.findViewById(R.id.textViewMessage);
        messageTextView.setVisibility(View.VISIBLE);

        ImageView avatar = (ImageView) view.findViewById(R.id.imageAvatar);
        if (avatar != null) {
            String avatarUrl = message.getSenderAvatarUrl();
            if (avatarUrl != null) {
                avatar.setVisibility(View.VISIBLE);
                avatar.setTag(R.id.webimMessage, message);
                if (!avatarUrl.equals(avatar.getTag(R.id.avatarUrl))) {
                    avatar.setTag(null);
                    Glide.with(adapter.getContext())
                            .load(avatarUrl)
                            .into(avatar);
                    avatar.setTag(R.id.avatarUrl, avatarUrl);
                    if (adapter.getOnAvatarClickListener() != null) {
                        avatar.setOnClickListener(adapter.getOnAvatarClickListener());
                    }
                }
            } else {
                avatar.setVisibility(View.GONE);
            }
        }
        TextView senderName = (TextView) view.findViewById(R.id.nameTV);
        if (senderName != null) {
            senderName.setVisibility(headerVisible ? View.VISIBLE : View.GONE);
            senderName.setText(message.getSenderName());
        }
        switch (message.getType()) {
            case FILE_FROM_OPERATOR:
                if (senderName != null) {
                    senderName.setVisibility(View.GONE);
                }
            case FILE_FROM_VISITOR:
                Message.Attachment attachment = message.getAttachment();
                if (attachment == null) {
                    messageTextView.setText(message.getText());
                } else {
                    final Message.ImageInfo imageInfo = attachment.getImageInfo();
                    final String fileUrl = attachment.getUrl();
                    if (imageInfo != null) {
                        final ImageView thumbView = (ImageView) view.findViewById(R.id.fileThumb);
                        thumbView.setVisibility(View.VISIBLE);
                        setViewSize(thumbView, getThumbSize(imageInfo));
                        messageTextView.setVisibility(View.GONE);
                        Glide.with(adapter.getContext())
                                .load(imageInfo.getThumbUrl())
                                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                                .bitmapTransform(new RoundedCornersTransformation(
                                        adapter.getContext(), 8, 0))
                                .into(thumbView);

                        thumbView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent openImgIntent = new Intent(v.getContext(), ImageActivity.class);
                                openImgIntent.setData(Uri.parse(fileUrl));
                                v.getContext().startActivity(openImgIntent);
                            }
                        });
                        break;
                    }
                    setMessageAsLink(adapter.getContext(), view,
                            messageTextView, message.getText(), fileUrl);
                }
                break;
            default:
                messageTextView.setText(message.getText());
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        String[] options = view.getResources()
                                .getStringArray(R.array.message_actions);
                        builder.setItems(options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case 0: // copy
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
                                }
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
                break;
        }
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if (sendingProgress != null) {
            sendingProgress.setVisibility(View.GONE);
        }
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if (messageTime != null) {
            messageTime.setText(adapter.getDateFormat().format(message.getTime()));
            messageTime.setVisibility(headerVisible ? View.VISIBLE : View.GONE);
        }
        return view;
    }

    private void showMessage(String message, View view) {
        Toast toast = Toast.makeText(view.getContext().getApplicationContext(),
                message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private static class Size {
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

    private static Size getThumbSize(Message.ImageInfo imageInfo) {
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        if (height > width) {
            width = THUMB_SIZE * width / height;
            height = THUMB_SIZE;
        } else {
            height = THUMB_SIZE * height / width;
            width = THUMB_SIZE;
        }
        return new Size(width, height);
    }

    private static void setViewSize(ImageView view, Size size) {
        if (view.getParent() instanceof FrameLayout) {
            view.setLayoutParams(new FrameLayout.LayoutParams(size.getWidth(),
                    size.getHeight(), Gravity.END));
        }
        else {
            view.setLayoutParams(new LinearLayout.LayoutParams(size.getWidth(),
                    size.getHeight(), Gravity.END));
        }
    }

    private void setMessageAsLink(final Context context,
                                  final View view,
                                  final TextView textView,
                                  final String text,
                                  final String url) {
        textView.setText(Html.fromHtml(context.getResources().getString(R.string.file_send)
                + "<a href=\"" + url + "\">" + text + "</a>"));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        ImageView redirect = (ImageView) view.findViewById(R.id.imageViewOpenFile);
        redirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (url != null) {
                    Uri uri = Uri.parse(url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    context.startActivity(intent);
                }
            }
        });
    }

    private int getLayout() {
        if (message.getSendStatus() == Message.SendStatus.SENDING) {
            return R.layout.item_local_message;
        }
        switch (message.getType()) {
            case OPERATOR:
                return R.layout.item_remote_message;
            case VISITOR:
                return R.layout.item_local_message;
            case INFO:
                return R.layout.item_info_message;
            case OPERATOR_BUSY:
                return R.layout.item_op_busy_message;
            case FILE_FROM_VISITOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? R.layout.item_local_message
                        : R.layout.item_send_file;
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null
                        || message.getAttachment().getImageInfo() != null
                        ? R.layout.item_remote_message
                        : R.layout.item_recieve_file;
            default:
                return R.layout.item_info_message;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WebimMessageListItem that = (WebimMessageListItem) o;

        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }
}
