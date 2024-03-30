package io.resttestgen.implementation.mutator.parameter;

import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.datatype.parameter.leaves.NumberParameter;
import io.resttestgen.core.datatype.parameter.leaves.StringParameter;
import io.resttestgen.core.helper.ExtendedRandom;
import io.resttestgen.core.testing.mutator.ParameterMutator;
import io.resttestgen.core.testing.parametervalueprovider.ParameterValueProvider;
import io.resttestgen.core.testing.parametervalueprovider.ParameterValueProviderCachedFactory;
import io.resttestgen.implementation.parametervalueprovider.ParameterValueProviderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConstraintViolationParameterMutator extends ParameterMutator {

    private static final Logger logger = LogManager.getLogger(ConstraintViolationParameterMutator.class);
    private static final ParameterValueProvider valueProvider =
            ParameterValueProviderCachedFactory.getParameterValueProvider(ParameterValueProviderType.RANDOM);

    @Override
    public boolean isParameterMutable(Parameter parameter) {
        if (parameter.getValue() == null) {
            return false;
        }

        // Check if parameter is an enum
        if (parameter.isEnum() && !parameter.getEnumValues().isEmpty()) {
            return true;
        }

        // Check if string parameters have length constraints
        else if (parameter instanceof StringParameter) {
            return ((StringParameter) parameter).getMaxLength() != null ||
                    (((StringParameter) parameter).getMinLength() != null &&
                            ((StringParameter) parameter).getMinLength() > 0);
        }

        // Check if number parameters have value constraints
        else if (parameter instanceof NumberParameter) {
            return ((NumberParameter) parameter).getMinimum() != null ||
                    ((NumberParameter) parameter).getMaximum() != null;
        }

        // Others parameters are not mutable
        return false;
    }

    @Override
    public Parameter mutate(Parameter parameter) {
        if (isParameterMutable(parameter)) {
            if (parameter.isEnum() && !parameter.getEnumValues().isEmpty()) {
                mutateEnum((LeafParameter) parameter);
            } else if (parameter instanceof StringParameter) {
                mutateString((StringParameter) parameter);
            } else if (parameter instanceof NumberParameter) {
                mutateNumber((NumberParameter) parameter);
            }
        } else {
            logger.warn("The provided parameter cannot be mutated because it does not provide constraints to violate.");
        }
        return parameter;
    }

    /**
     * Assigns to the parameter a value outside the enum constraints
     * @param parameter the parameter to mutate
     */
    private void mutateEnum(LeafParameter parameter) {
        // Set a random value to the parameter (it is very unlikely that the generated value belongs to the enum values)
        // FIXME: check that the generated value does not belong to the enum values
        parameter.setValueWithProvider(valueProvider);
    }

    /**
     * String length is changed to violate length constraints
     * @param parameter the parameter to mutate
     */
    private void mutateString(StringParameter parameter) {

        ExtendedRandom random = Environment.getInstance().getRandom();

        // Save to this list the possible lengths of the mutated string
        List<Integer> lengths = new ArrayList<>();
        if (parameter.getMinLength() != null && parameter.getMinLength() > 1) {
            lengths.add(random.nextLength(0, parameter.getMinLength()));
        }
        if (parameter.getMaxLength() != null && parameter.getMaxLength() < Integer.MAX_VALUE - 1) {
            lengths.add(random.nextLength(parameter.getMaxLength() + 1, Integer.MAX_VALUE));
        }

        // Choose a random length
        Optional<Integer> chosenLength = random.nextElement(lengths);

        // If the current value is longer, just cut the string
        if (chosenLength.isPresent() && ((String) parameter.getConcreteValue()).length() > chosenLength.get()) {
            parameter.setValueManually(((String) parameter.getConcreteValue()).substring(0, chosenLength.get()));
        }

        // If the current value is shorter, add random characters
        else if (chosenLength.isPresent() && ((String) parameter.getConcreteValue()).length() < chosenLength.get()) {
            parameter.setValueManually(parameter.getConcreteValue() +
                    random.nextRandomString(chosenLength.get() - ((String) parameter.getConcreteValue()).length()));
        }
    }

    /**
     * Number value is changed to violate value constraints
     * FIXME: generate number (superclass) instead of double
     * @param parameter the parameter to mutate
     */
    private void mutateNumber(NumberParameter parameter) {
        ExtendedRandom random = Environment.getInstance().getRandom();

        // Assuming parameter.getMinimum() and parameter.getMaximum() define the range inclusively.
        double minBound = (parameter.getMinimum() != null) ? parameter.getMinimum() : Double.MIN_VALUE;
        double maxBound = (parameter.getMaximum() != null) ? parameter.getMaximum() : Double.MAX_VALUE;

        // Adjust bounds if they are set to their extreme values which might not be the intended use case.
        if (minBound == Double.MIN_VALUE) {
            minBound = 0; // Assuming you want the minimum to be 0 if not specified.
        }
        if (maxBound == Double.MAX_VALUE) {
            maxBound = 1; // Adjust this based on the expected maximum if not specified.
        }

        // Ensure the bounds are valid for the random generation.
        if (minBound < maxBound) {
            double chosenValue = random.nextDouble(minBound, maxBound);
            parameter.setValueManually(chosenValue);
        } else {
            // Handle the case where the min and max bounds do not form a valid range.
            // This could log an error, throw an exception, or take some other action as appropriate.
            System.err.println("Invalid bounds for mutateNumber: minBound >= maxBound");
        }
    }


    @Override
    public boolean isErrorMutator() {
        return true;
    }
}
