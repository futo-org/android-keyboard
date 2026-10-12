package org.futo.inputmethod.latin

import org.futo.inputmethod.latin.makedict.DictionaryHeader
import org.futo.inputmethod.latin.makedict.FormatSpec
import java.util.Locale

/**
 * A pinyin syllable lexicon exposed as a native ITrie for the swipe decoder's
 * trie-constrained beam search. Pinyin is written with plain a-z (v stands for ü),
 * so the existing layout-agnostic swipe encoder emits these directly.
 */
class PinyinSwipeTrie {

    private val binary = BinaryDictionary(
        "pinyin_swipe", true /* useFullEditDistance */, Locale("zh"), "pinyin_swipe",
        FormatSpec.VERSION4.toLong(),
        mapOf(
            DictionaryHeader.DICTIONARY_ID_KEY to "pinyin_swipe",
            DictionaryHeader.DICTIONARY_LOCALE_KEY to "zh",
            DictionaryHeader.DICTIONARY_VERSION_KEY to "1",
        ),
    )

    init {
        // ponytail: uniform frequency; the swipe encoder + rime do the ranking.
        for (word in SYLLABLES) {
            binary.addUnigramEntry(word, 100, null, 0, false, false, false, 0)
        }
    }

    /** Returns a native trie handle over the given letters, or 0 if it can't be built. */
    fun handle(letters: String): Long = binary.getITrie(letters, false)

    /** Whether the lexicon contains this syllable (for tests/diagnostics). */
    fun contains(word: String): Boolean = binary.isInDictionary(word)

    companion object {
        // Toneless Mandarin syllables (ü -> v), sorted. ~410 entries.
        val SYLLABLES: List<String> = """
            a ai an ang ao
            ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
            ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong
            chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
            da dai dan dang dao de dei deng di dia dian diao die ding diu dong dou du duan dui dun duo
            e ei en eng er
            fa fan fang fei fen feng fo fou fu
            ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
            ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
            ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
            ka kai kan kang kao ke ken keng kong kou ku kua kuai kuan kuang kui kun kuo
            la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu long lou lu
            luan lun luo lv lve
            ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
            na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu
            nuan nuo nv nve
            o ou
            pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
            qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
            ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
            sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou
            shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo
            ta tai tan tang tao te teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
            wa wai wan wang wei wen weng wo wu
            xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
            ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
            za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi
            zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
        """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}
