package com.webimapp.android.demo.client.items;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.webimapp.android.demo.client.MessagesAdapter;
import com.webimapp.android.demo.client.R;
import com.webimapp.android.sdk.Message;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;

public class WebimMessageListItem extends ListItem {
    private static final int THUMB_SIZE = 300; // Should be the same as in the server configuration
    private static final int NUMBER_OF_MS_IN_DAY = 1000 * 60 * 60 * 24;
    private final @NonNull Message message;
    private final ViewType viewType;

    public WebimMessageListItem(@NonNull Message message) {
        this.message = message;
        this.viewType = computeItemType(message);
    }

    @Override
    public ViewType getViewType() {
        return viewType;
    }

    private static ViewType computeItemType(Message message) {
        if(message.getSendStatus() == Message.SendStatus.SENDING)
            return ViewType.VISITOR;
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
                return message.getAttachment() == null || message.getAttachment().getImageInfo() != null ? ViewType.VISITOR : ViewType.FILE_FROM_VISITOR;
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null || message.getAttachment().getImageInfo() != null ? ViewType.OPERATOR : ViewType.FILE_FROM_OPERATOR;
            default:
                return ViewType.INFO;
        }
    }

    @NonNull
    public Message getMessage() {
        return message;
    }

    @Override
    public View getView(final MessagesAdapter adapter, @Nullable View convertView, ViewGroup parent, ListItem prev) {
        int layoutId = getLayout();

        View view = convertView;
        if(convertView != null) {
            Object tag = convertView.getTag(R.id.webimMessage);
            if(tag != null) {
                if(tag == message)
                    return convertView;
                Message.Type type = ((Message)tag).getType();
                if(type == Message.Type.FILE_FROM_OPERATOR || type == Message.Type.FILE_FROM_VISITOR)
                    view = null;
            }
        }
        if(view == null)
            view = adapter.getInflater().inflate(layoutId, parent, false);
        return message.getSendStatus() == Message.SendStatus.SENDING ? getSendingMessageView(adapter, view) : getRealMessageView(adapter, view, prev);
    }

    public View getSendingMessageView(final MessagesAdapter adapter, @NonNull View view) {
        if(message.getType() == Message.Type.FILE_FROM_OPERATOR || message.getType() == Message.Type.FILE_FROM_VISITOR)
            return getSendingFileMessageView(adapter, view);
        else
            return getSendingTextMessageView(adapter, view);
    }

    public View getSendingTextMessageView(final MessagesAdapter adapter, @NonNull View view) {
        TextView messageTextView = (TextView) view.findViewById(R.id.textViewMessage);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(message.getText());
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if(sendingProgress != null)
            sendingProgress.setVisibility(View.VISIBLE);
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if(messageTime != null)
            messageTime.setVisibility(View.GONE);
        final ImageView thumb = (ImageView) view.findViewById(R.id.fileThumb);
        if(thumb != null)
            thumb.setVisibility(View.GONE);
        return view;
    }

    public View getSendingFileMessageView(MessagesAdapter adapter, @NonNull View view) {
        TextView messageTextView = (TextView) view.findViewById(R.id.textViewMessage);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(R.string.file_upload_message_text);
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if(sendingProgress != null)
            sendingProgress.setVisibility(View.VISIBLE);
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if(messageTime != null)
            messageTime.setVisibility(View.GONE);
        final ImageView thumb = (ImageView) view.findViewById(R.id.fileThumb);
        if(thumb != null)
            thumb.setVisibility(View.GONE);
        return view;
    }

    public View getRealMessageView(final MessagesAdapter adapter, @NonNull View view, @Nullable ListItem prev) {
        view.setTag(R.id.webimMessage, message);
        TextView date = (TextView) view.findViewById(R.id.dateView);
        if (date != null) {
            if (prev == null || (message.getTime() / NUMBER_OF_MS_IN_DAY != ((WebimMessageListItem) prev).getMessage().getTime() / NUMBER_OF_MS_IN_DAY)) {
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
            if(avatarUrl != null) {
                avatar.setVisibility(View.VISIBLE);
                avatar.setTag(R.id.webimMessage, message);
                if(!avatarUrl.equals(avatar.getTag(R.id.avatarUrl))) {
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
            senderName.setVisibility(View.VISIBLE);
            senderName.setText(message.getSenderName());
        }
        switch (message.getType()) {
            case FILE_FROM_OPERATOR:
                if(senderName != null)
                    senderName.setVisibility(View.GONE);
            case FILE_FROM_VISITOR:
                Message.Attachment attachment = message.getAttachment();
                if(attachment == null) {
                    messageTextView.setText(message.getText());
                } else {
                    final Message.ImageInfo imageInfo = attachment.getImageInfo();
                    final String fileUrl = attachment.getUrl();
                    if(imageInfo != null) {
                        final ImageView thumb = (ImageView) view.findViewById(R.id.fileThumb);
                        thumb.setVisibility(View.VISIBLE);
                        setViewSize(thumb, getThumbSize(imageInfo));
                        messageTextView.setVisibility(View.GONE);
                        Glide.with(adapter.getContext())
                                .load(imageInfo.getThumbUrl())
                                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                                .bitmapTransform(new RoundedCornersTransformation(adapter.getContext(), 8, 0))
                                .into(thumb);
                        thumb.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                                View view = View.inflate(v.getContext(), R.layout.image_bar, null);
                                final WebView image = (WebView) view.findViewById(R.id.imageView);
                                image.getSettings().setSupportZoom(true);
                                image.getSettings().setBuiltInZoomControls(true);
                                image.setPadding(0, 0, 0, 0);
                                image.setScrollbarFadingEnabled(true);
                                image.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
                                image.loadUrl(fileUrl);
                                builder.setView(view);
                                final AlertDialog dialog = builder.create();
                                dialog.show();

                                Button options = (Button) view.findViewById(R.id.options_button);
                                options.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                                        View v = View.inflate(view.getContext(), R.layout.buttons_for_image, null);
                                        builder.setView(v);
                                        final AlertDialog dialog = builder.create();
                                        dialog.show();

                                        Button browser = (Button) v.findViewById(R.id.browser_button);
                                        browser.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                Uri uri = Uri.parse(fileUrl);
                                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                                adapter.getContext().startActivity(intent);
                                            }
                                        });

                                        Button copyURI = (Button) v.findViewById(R.id.copy_button);
                                        copyURI.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                ClipData clip = ClipData.newUri(view.getContext().getContentResolver(), "URI", Uri.parse(fileUrl));
                                                ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                clipboard.setPrimaryClip(clip);
                                            }
                                        });

                                        final Button saveImage = (Button) v.findViewById(R.id.save_button);
                                        saveImage.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                showMessage("Загрузка файла...", view);
                                                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
                                                Long time = System.currentTimeMillis();
                                                request.setTitle("image_" + time);

                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                                    request.allowScanningByMediaScanner();
                                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                                }
                                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "image_" + time + ".jpg");

                                                DownloadManager manager = (DownloadManager) view.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                                                manager.enqueue(request);
                                                showMessage("Файл image_" + time + ".jpg сохранён.", view);
                                            }
                                        });

                                    }
                                });
                            }
                        });
                        break;
                    }
                    setMessageAsLink(adapter.getContext(), view, messageTextView, message.getText(), fileUrl);
                }
                break;
            default:
                messageTextView.setText(message.getText());
                break;
        }
        View sendingProgress = view.findViewById(R.id.sendingProgress);
        if(sendingProgress != null)
            sendingProgress.setVisibility(View.GONE);
        TextView messageTime = (TextView) view.findViewById(R.id.messageTime);
        if(messageTime != null){
            messageTime.setText(adapter.getDateFormat().format(message.getTime()));
            messageTime.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private void showMessage(String message, View view) {
        Toast toast = Toast.makeText(view.getContext().getApplicationContext(), message, Toast.LENGTH_SHORT);
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
        if(height > width) {
            width = THUMB_SIZE * width / height;
            height = THUMB_SIZE;
        } else {
            height = THUMB_SIZE * height / width;
            width = THUMB_SIZE;
        }
        return new Size(width, height);
    }

    private static void setViewSize(ImageView view, Size size) {
        if(view.getParent() instanceof FrameLayout)
            view.setLayoutParams(new FrameLayout.LayoutParams(size.getWidth(), size.getHeight(), Gravity.END));
        else
            view.setLayoutParams(new LinearLayout.LayoutParams(size.getWidth(), size.getHeight(), Gravity.END));
    }

    private void setMessageAsLink(final Context context, final View view, final TextView textView, final String text, final String url) {
        textView.setText(Html.fromHtml(context.getResources().getString(R.string.file_send) + "<a href=\"" + url + "\">" + text + "</a>"));
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
        if(message.getSendStatus() == Message.SendStatus.SENDING)
            return R.layout.item_local_message;
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
                return message.getAttachment() == null || message.getAttachment().getImageInfo() != null ? R.layout.item_local_message : R.layout.item_send_file;
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null || message.getAttachment().getImageInfo() != null ? R.layout.item_remote_message : R.layout.item_recieve_file;
            default:
                return R.layout.item_info_message;
        }
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        WebimMessageListItem that = (WebimMessageListItem) o;

        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }
}
