package com.aiyi.game.dnfserver.pvf;

import com.xiaoyouma.dnf.parser.npk.coder.NpkCoder;
import com.xiaoyouma.dnf.parser.npk.model.NpkTexture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * NPK管理器
 * @author xiatian
 */
@Component
public class NpkManager {

    protected static final Logger logger = LoggerFactory.getLogger(NpkManager.class);

    @PostConstruct
    public void init(){
        File file = new File("data/ImagePacks2");
        if (!file.exists()){
            logger.warn("Folder ImagePacks2 not found!");
        }else{
            NpkCoder.initialize(file.getAbsolutePath());
        }
    }

    /**
     * 获取图片字节数组
     * @param path 图片路径
     * @param index 图片索引
     * @return 图片字节数组
     */
    public byte[] getImageBytes(String path, int index){
        NpkTexture[] textures = null;
        try {
            textures = NpkCoder.loadImg(path).getTextures();
        }catch (Exception e){
            return new byte[0];
        }
        if (null == textures || textures.length <= index){
            return new byte[0];
        }
        return textures[index].toPngBytes();
    }
}
