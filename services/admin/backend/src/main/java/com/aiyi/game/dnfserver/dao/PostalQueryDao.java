package com.aiyi.game.dnfserver.dao;

import com.aiyi.game.dnfserver.entity.Postal;
import org.apache.ibatis.annotations.*;
import java.util.Date;
import java.util.List;

/** Read-only projection. Account IDs and character IDs belong to different namespaces. */
@Mapper
public interface PostalQueryDao {
    String FILTER = "<where>"
        + "<if test=\"account != null or !administrator\">"
        + "EXISTS (SELECT 1 FROM taiwan_cain.charac_info c "
        + "JOIN d_taiwan.accounts a ON a.UID=c.m_id WHERE c.charac_no=p.receive_charac_no "
        + "<if test=\"account != null\">AND a.accountname=#{account} </if>"
        + "<if test=\"!administrator\">AND (a.UID=#{actor} OR a.parent_uid=#{actor}) </if>)"
        + "</if>"
        + "<if test=\"start != null\">AND p.occ_time &gt;= #{start}</if>"
        + "<if test=\"end != null\">AND p.occ_time &lt;= #{end}</if>"
        + "</where>";

    @Select("<script>SELECT p.postal_id AS postalId,p.occ_time AS occTime,"
        + "p.send_charac_no AS sendCharacNo,p.send_charac_name AS sendCharacName,"
        + "p.receive_charac_no AS receiveCharacNo,p.item_id AS itemId,p.add_info AS addInfo,"
        + "p.`upgrade` AS upgrade,p.amplify_option AS amplifyOption,p.amplify_value AS amplifyValue,"
        + "p.gold,p.delete_flag AS deleteFlag,p.seal_flag AS sealFlag,p.letter_id AS letterId,"
        + "p.seperate_upgrade AS seperateUpgrade FROM taiwan_cain_2nd.postal p " + FILTER
        + " ORDER BY p.postal_id DESC LIMIT #{offset},#{size}</script>")
    List<Postal> list(@Param("account") String account,@Param("administrator") boolean administrator,
        @Param("actor") long actor,@Param("start") Date start,@Param("end") Date end,
        @Param("offset") int offset,@Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM taiwan_cain_2nd.postal p " + FILTER + "</script>")
    int count(@Param("account") String account,@Param("administrator") boolean administrator,
        @Param("actor") long actor,@Param("start") Date start,@Param("end") Date end);
}
