package ru.webim.chatview.holders;

import static ru.webim.android.sdk.Message.Type.FILE_FROM_OPERATOR;

import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.net.MalformedURLException;
import java.net.URL;

import coil.ComponentRegistry;
import coil.ImageLoader;
import coil.decode.SvgDecoder;
import coil.request.ImageRequest;
import ru.webim.android.sdk.Message;
import ru.webim.chatview.R;
public class ReceivedMessageHolder extends FileMessageHolder {

    private final static String SVG_EXTENSION = ".svg";
    boolean showSenderInfo = true;
    TextView timeText;
    TextView nameText;
    TextView nameTextForImage;
    TextView nameTextForFile;
    ImageView profileImage;

    public ReceivedMessageHolder(View itemView, ChatHolderActions holderActions) {
        super(itemView, holderActions);

        messageBackground = R.drawable.background_received_message;
        bubbleColor = R.color.received_msg_bubble;
        selectedColor = R.color.received_msg_select;

        timeText = itemView.findViewById(R.id.text_message_time);
        nameText = itemView.findViewById(R.id.sender_name);
        nameTextForImage = itemView.findViewById(R.id.sender_name_for_image);
        nameTextForFile = itemView.findViewById(R.id.sender_name_for_file);
        profileImage = itemView.findViewById(R.id.sender_photo);
    }

    @Override
    public void bind(Message message, boolean showDate) {
        super.bind(message, showDate);

        timeText.setText(DateFormat.getTimeFormat(context)
            .format(message.getTime()));
        if (showSenderInfo) {
            if (message.getType().equals(FILE_FROM_OPERATOR) && fileIsImage(message)) {
                nameTextForImage.setVisibility(View.VISIBLE);
                nameTextForImage.setText(message.getSenderName());
            } else {
                nameTextForImage.setVisibility(View.GONE);
                if (message.getType().equals(FILE_FROM_OPERATOR)) {
                    nameTextForFile.setVisibility(View.VISIBLE);
                    nameTextForFile.setText(message.getSenderName());
                } else {
                    nameText.setVisibility(View.VISIBLE);
                    nameText.setText(message.getSenderName());
                }
            }

            String avatarUrl = message.getSenderAvatarUrl();
            profileImage.setVisibility(View.VISIBLE);
            if (avatarUrl != null) {
                if (!avatarUrl.equals(profileImage.getTag(R.id.avatarUrl))) {
                    loadProfileImage(avatarUrl,profileImage);
                    profileImage.setTag(R.id.avatarUrl, avatarUrl);
                }
            } else {
                Resources resources = context.getResources();
                profileImage.setImageDrawable(resources.getDrawable(R.drawable.default_operator_avatar));
                profileImage.setVisibility(View.VISIBLE);
            }
        } else {
            nameText.setVisibility(View.GONE);
            nameTextForImage.setVisibility(View.GONE);
            profileImage.setVisibility(View.GONE);
            showSenderInfo = true;
        }
    }

    private void loadProfileImage(String url, ImageView imageView) {
        try {
            String path = new URL(url).getPath().toLowerCase();
            if (path.endsWith(SVG_EXTENSION)) {
                ComponentRegistry componentRegistry = new ComponentRegistry.Builder()
                        .add(new SvgDecoder.Factory())
                        .build();
                ImageLoader imageLoader = new ImageLoader.Builder(context)
                        .components(componentRegistry)
                        .build();
                ImageRequest request = new ImageRequest.Builder(context)
                        .data(url)
                        .target(imageView)
                        .build();
                imageLoader.enqueue(request);
            } else {
                Glide.with(context).load(url).into(profileImage);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private boolean fileIsImage(Message message) {
        return message.getAttachment() != null && message.getAttachment().getFileInfo().getImageInfo() != null;
    }
}