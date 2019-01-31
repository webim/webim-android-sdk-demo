package com.webimapp.android.demo.client;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageHolder> {
    private static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

    private final List<Message> messageList;
    private final WebimChatFragment webimChatFragment;

    MessagesAdapter(WebimChatFragment webimChatFragment) {
        this.webimChatFragment = webimChatFragment;
        this.messageList = new ArrayList<>();
    }

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
            if ((message.getType().equals(Message.Type.OPERATOR)
                            || message.getType().equals(Message.Type.FILE_FROM_OPERATOR))
                    && (prev.getType().equals(Message.Type.OPERATOR)
                            || prev.getType().equals(Message.Type.FILE_FROM_OPERATOR))
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
        if (message.getSendStatus() == Message.SendStatus.SENDING) {
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

        MessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            messageDate = itemView.findViewById(R.id.text_message_date);
            messageTime = itemView.findViewById(R.id.text_message_time);
            messageTick = itemView.findViewById(R.id.tick);
        }

        public void bind(Message message, boolean showDate) {
            this.message = message;

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
                messageTick.setVisibility(message.getSendStatus() == Message.SendStatus.SENT
                        ? View.VISIBLE
                        : View.GONE);
            }
        }
    }

    class FileMessageHolder extends MessageHolder {
        // Should be the same as in the server configuration
        private static final int THUMB_SIZE = 300;

        ImageView thumbView;

        FileMessageHolder(View itemView) {
            super(itemView);
            thumbView = itemView.findViewById(R.id.attached_image);
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            thumbView.setVisibility(View.GONE);

            Message.Attachment attachment = message.getAttachment();
            if (attachment == null) {
                if (thumbView != null && message.getType().equals(Message.Type.FILE_FROM_VISITOR)) {
                    thumbView.setVisibility(View.GONE);
                    messageText.setText(R.string.uploading_file);
                    messageText.setVisibility(View.VISIBLE);
                }
                return;
            }
            final Message.ImageInfo imageInfo = attachment.getImageInfo();
            final String fileUrl = attachment.getUrl();
            if (imageInfo == null) {
                if (thumbView != null) {
                    thumbView.setVisibility(View.GONE);
                }
                messageText.setText(Html.fromHtml(webimChatFragment.getResources().getString(R.string.file_send)
                        + "<a href=\"" + fileUrl + "\">" + message.getText() + "</a>"));
                messageText.setVisibility(View.VISIBLE);
                return;
            }

            if (thumbView != null) {
                setViewSize(thumbView, getThumbSize(imageInfo));
                Glide.with(webimChatFragment)
                        .load(imageInfo.getThumbUrl())
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .bitmapTransform(new RoundedCornersTransformation(
                                webimChatFragment.getContext(), 8, 0))
                        .into(thumbView);
                thumbView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent openImgIntent = new Intent(v.getContext(), ImageActivity.class);
                        openImgIntent.setData(Uri.parse(fileUrl));
                        v.getContext().startActivity(openImgIntent);
                    }
                });
                messageText.setVisibility(View.GONE);
                thumbView.setVisibility(View.VISIBLE);
            }
        }

        private Size getThumbSize(Message.ImageInfo imageInfo) {
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

        SentMessageHolder(View itemView) {
            super(itemView);

            sendingProgress = itemView.findViewById(R.id.sending_msg);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    String[] options = view.getResources().getStringArray(message.canBeEdited()
                            ? R.array.sent_message_actions
                            : R.array.received_message_actions);
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
                                    break;
                                case 1: // edit
                                    webimChatFragment.onEditAction(message);
                                    break;
                                case 2: // delete
                                    webimChatFragment.onDeleteMessageAction(message);
                                    break;
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            boolean sending = message.getSendStatus() == Message.SendStatus.SENDING;
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
        TextView timeText, nameText;
        ImageView profileImage;

        ReceivedMessageHolder(View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.text_message_time);
            nameText = itemView.findViewById(R.id.sender_name);
            profileImage = itemView.findViewById(R.id.sender_photo);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    String[] options = view.getResources()
                            .getStringArray(R.array.received_message_actions);
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
                                    break;
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
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
                nameText.setVisibility(View.VISIBLE);
                nameText.setText(message.getSenderName());

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
                                    .getDrawable(R.drawable.default_operator_avatar_40x40));
                    profileImage.setVisibility(View.VISIBLE);
                }
            } else {
                nameText.setVisibility(View.GONE);
                profileImage.setVisibility(View.GONE);
                showSenderInfo = true;
            }
        }
    }
}