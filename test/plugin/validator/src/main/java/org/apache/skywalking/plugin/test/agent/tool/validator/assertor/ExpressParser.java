package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.ElementAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.EqualsAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.GreatThanAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.GreetEqualAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.NoopAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.NotEqualsAssertor;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element.NotNullAssertor;

public class ExpressParser {
    public static ElementAssertor parse(String express) {
        if (express == null) {
            return new NoopAssertor();
        }

        String expressTrim = express.trim();
        if (expressTrim.equals("not null")) {
            return new NotNullAssertor();
        }

        if (expressTrim.equals("null")) {
            return new NotNullAssertor();
        }

        String[] expressSegment = expressTrim.split(" ");
        if (expressSegment.length == 1) {
            return new EqualsAssertor(expressSegment[0]);
        } else if (expressSegment.length == 2) {
            String exceptedValue = expressSegment[1];
            switch (expressSegment[0].trim()) {
                case "nq":
                    return new NotEqualsAssertor(exceptedValue);
                case "eq":
                    return new EqualsAssertor(exceptedValue);
                case "gt":
                    return new GreatThanAssertor(exceptedValue);
                case "ge":
                    return new GreetEqualAssertor(exceptedValue);
            }
        }

        return new EqualsAssertor(express);
    }
}
