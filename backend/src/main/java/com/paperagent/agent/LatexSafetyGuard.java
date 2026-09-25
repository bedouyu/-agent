package com.paperagent.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 本地的保守检查：模型不能决定是否跳过这些检查。 */
@Component
public class LatexSafetyGuard {

    private static final Pattern PROTECTED_COMMAND = Pattern.compile(
            "\\\\(?:cite|citep|citet|ref|eqref|autoref|label|begin|end)\\{[^{}]*}"
    );

    public List<String> inspect(String original, String suggested) {
        List<String> warnings = new ArrayList<>();
        if (suggested == null || suggested.isBlank()) {
            warnings.add("建议内容为空");
            return warnings;
        }
        if (!commandCounts(original).equals(commandCounts(suggested))) {
            warnings.add("引用、标签或 LaTeX 环境命令发生变化，请人工核对");
        }
        if (countUnescapedDollars(original) != countUnescapedDollars(suggested)) {
            warnings.add("数学模式分隔符 $ 的数量发生变化，请人工核对");
        }
        if (suggested.length() > 6_000) {
            warnings.add("建议过长，已跳过模型复核");
        }
        return warnings;
    }

    private Map<String, Integer> commandCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = PROTECTED_COMMAND.matcher(text);
        while (matcher.find()) {
            counts.merge(matcher.group(), 1, Integer::sum);
        }
        return counts;
    }

    private int countUnescapedDollars(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '$') {
                continue;
            }
            int backslashes = 0;
            for (int previous = index - 1; previous >= 0 && text.charAt(previous) == '\\'; previous--) {
                backslashes++;
            }
            if (backslashes % 2 == 0) {
                count++;
            }
        }
        return count;
    }
}
