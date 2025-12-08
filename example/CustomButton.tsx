import React from 'react';
import { TouchableOpacity, Text, StyleSheet, TextStyle, ViewStyle } from 'react-native';

interface CustomButtonProps {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  color?: string;
}

const CustomButton: React.FC<CustomButtonProps> = ({
  title,
  onPress,
  disabled = false,
  color = '#2196F3',
}) => {
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled}
      style={[
        styles.customButton,
        { backgroundColor: disabled ? '#cccccc' : color },
      ]}
      activeOpacity={0.7}
    >
      <Text
        style={[
          styles.customButtonText,
          { color: disabled ? '#666666' : 'white' },
        ]}
      >
        {title}
      </Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  customButton: {
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 5,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 40,
  },
  customButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
});

export default CustomButton;
