package com.resumecopilot.match;

import com.resumecopilot.entity.SkillAlias;
import com.resumecopilot.mapper.SkillAliasMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 t_skill_alias 装载 {@link SkillDictionary}（规则通道字典的 DB 侧出口）。
 *
 * <p>每次匹配现查现用而不是启动缓存：表只有几十行，一次 selectList 微秒级，
 * 换来的收益是"字典改了立即生效、无需重启"——字典恰恰是需要频繁迭代的部件
 * （badcase 加一行别名就是一次迭代），缓存它属于提前优化掉正确性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillDictionaryService {

    private final SkillAliasMapper skillAliasMapper;

    public SkillDictionary load() {
        List<SkillAlias> rows = skillAliasMapper.selectList(null);
        List<String[]> mappings = new ArrayList<>(rows.size());
        for (SkillAlias r : rows) {
            mappings.add(new String[]{r.getStandardName(), r.getAlias()});
        }
        log.debug("skill dictionary loaded: {} rows", rows.size());
        return new SkillDictionary(mappings);
    }
}
