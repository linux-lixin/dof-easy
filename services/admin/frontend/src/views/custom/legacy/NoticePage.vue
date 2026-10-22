<script setup lang="ts">
import LegacyLayout from './LegacyLayout.vue';
import { useLegacyScripts } from '../../../composables/legacy/useLegacyScripts';
import { computed } from 'vue';

useLegacyScripts(true);

type NoticeItem = {
  title: string;
  content: string;
};

// 按时间轴顺序平铺展示（从想法 -> 开放）
const notices: NoticeItem[] = [
  {
    title: '关于想法',
    content:
      '很多人第一次认识这款游戏，都是在放学后的网吧、在拐角的小卖部、在那条回家的路上。\n\n我也是。那时候我们口袋里没多少钱，装备也不华丽，但一进赛丽亚的房间，就觉得世界被点亮了。和朋友约好几点上线、一起刷图、一起爆装备，哪怕只是掉了一件蓝装也能开心很久。\n\n后来生活把人往前推：考试、工作、家庭……游戏被迫按下暂停。等再回头想打开它时才发现，时间已经把很多东西带走了——老朋友不再上线，电脑里也没有当年的截图。\n\n所以我想做一件“很傻但很真”的事：把那份感觉尽可能找回来。不是为了盈利，不是为了噱头，只是想给同样怀念的人一个地方：你随时可以回来，像当年一样，喊一句“来不来？”\n\n这个公益服，就是从这句“我想再玩一次”开始的。'
  },
  {
    title: '60版本小规模内测',
    content:
      '正式对外之前，我和我的几个网友门选择从 60 版本起步做了一次小规模内测。\n\n那段时间很像“搭一座桥”的过程：服务器稳定不稳定？刷图会不会掉线？任务流程能不能跑通？装备、掉落、金币、交易、邮件……每一个细节都可能把人的期待击碎。\n\n我们和第一批参与者一起一点点把坑填平：有人把每次异常的复现步骤写得比作业还认真，有人一句“我又上号试了下，好了！”让人突然想起当年的队友。\n\n内测的意义不止是验证功能，更像一次互相确认：原来不只是我一个人，还在怀念那段时光。'
  },
  {
    title: '开发制作了游戏登录器',
    content:
      '在测试过程中, 游戏修修补补, 为了让大家进入游戏更简单、更省心，我开发了带自动更新的专用登录器。\n\n你不用再为版本不一致、补丁缺失、各种文件对不上而反复折腾；登录器会把“麻烦”留给系统，把“轻松”留给你。\n\n很多人已经不再是学生了，能用来玩游戏的时间很碎：下班后的一小时、哄孩子睡着后的半小时、周末的一个下午。\n\n所以我们希望你点下“开始”之后，看到的是熟悉的画面，而不是一串又一串报错。'
  },
  {
    title: '开发制作了游戏后台',
    content:
      '\n\n我们开发了配套后台系统，用来管理公告、配置、版本、玩家相关数据, 游戏装备与日常运维。\n\n我曾对参与内侧的大家承诺过, 这游戏是我的, 也是你们的, 这个后台的管理员中应当有你们的位置. \n\n很多工作看起来不起眼：处理异常、修复漏洞、整理 PVF、优化接口性能……但这些才是“你能顺畅刷图”的底色。\n\n你不一定会注意到它的存在，但每一次稳定上线、每一次更新顺利、每一次问题被及时解决，都是后台在默默把事情做好。'
  },
  {
    title: '登录器和后台已经开源',
    content:
      '我选择把登录器和后台开源。\n\n开源不是一句口号，而是一种态度：让大家看得见我们做了什么，也让更多懂技术的人能一起参与、一起改进、一起把坑填平。\n\n对于公益服来说，“透明”是最珍贵的信任。\n\n希望你在这里能放心地玩，也欢迎你把你的建议、你的想法、你的代码提交给我们——这个世界最动人的事情之一，就是陌生人因为同一个热爱而并肩。'
  },
  {
    title: '第二次重构和升级（已开源）',
    content:
      '第一次上线之后，我很快就迎来了第二次重构与升级。\n\n我必须把一些“能跑就行”的东西，打磨成“长期可用”。即使是一个窗口的阴影和边框颜色也经历了数小时的大模. \n\n重构很痛苦，也很浪漫。它意味着推倒重来，意味着承认不足，意味着在无数个夜晚对着代码和日志反复较劲。\n\n听到大家说用起来变舒服了，就觉得一切都值得。我们也继续保持开源，让改变有迹可循。'
  },
  {
    title: '版本规划：暂定 70 版本',
    content:
      '现在, 我暂定为 70 版本。\n\n这是很多人心里最“刚刚好”的阶段：既保留了经典玩法与节奏，也拥有更完整的内容与成长空间。\n\n我们希望它既能让老玩家找回熟悉的手感，也能让回归玩家有持续探索的目标。\n\n如果你曾经在某个版本停下脚步，希望这一次，你能走得更远一点。'
  },
  {
    title: '本服正式对外开放',
    content:
      '今天，我正式对外开放。但与测试版本进行了分离, 这里没有满屏的测试员和报错的弹窗. \n\n这不是一条简单的公告，而是很多个夜晚、很多次崩溃与修复、很多句“再试一次”的结果。\n\n如果你此刻点开公告，是因为你也曾在这款游戏里留下些什么——一段友情、一段青春、一段不愿被遗忘的时光——那就回来看看吧。\n\n不需要盛大仪式，你上线，我们就在。\n\n欢迎回家，勇士。'
  }
];

const reversedNotices = computed(() => [...notices].reverse());
</script>

<template>
  <LegacyLayout>
    <!--Page Title-->
    <section class="page-banner" style="background-image:url(/assets/legacy/images/background/title-bg.jpg);">
      <div class="auto-container">
        <div class="inner-container clearfix">
          <ul class="bread-crumb clearfix">
            <li><a href="/home">主页</a></li>
            <li>公告</li>
          </ul>
          <h1>公告</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Timeline Section -->
    <section class="faq-section" style="padding-top: 90px; padding-bottom: 90px;">
      <div class="auto-container">
        <div class="sec-title centered">
          <div class="title">Timeline</div>
          <h2>更新记录 & 里程碑</h2>
        </div>

        <div class="timeline">
          <div v-for="(item, idx) in reversedNotices" :key="idx" class="timeline-item wow fadeInUp" :data-wow-delay="`${idx * 80}ms`">
            <div class="timeline-dot"></div>
            <div class="timeline-card">
              <h3 class="timeline-title">{{ item.title }}</h3>
              <div class="timeline-content" style="white-space: pre-line;">
                {{ item.content }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End Timeline Section -->
  </LegacyLayout>
</template>

<style scoped>
/* 时间轴：平铺 + 左侧线条 */
.timeline {
  position: relative;
  margin: 0 auto;
  max-width: 920px;
  padding-left: 26px;
}

.timeline:before {
  content: '';
  position: absolute;
  left: 9px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: rgba(255, 255, 255, 0.12);
}

.timeline-item {
  position: relative;
  padding: 0 0 26px 0;
}

.timeline-dot {
  position: absolute;
  left: 0;
  top: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #3786ff;
  box-shadow: 0 0 0 4px rgba(55, 134, 255, 0.18);
}

.timeline-card {
  margin-left: 26px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 18px 18px 16px;
}

.timeline-title {
  color: #ffffff;
  font-size: 22px;
  margin-bottom: 8px;
}

.timeline-content {
  color: rgba(255, 255, 255, 0.75);
  line-height: 1.8em;
  font-size: 15px;
}
</style>
