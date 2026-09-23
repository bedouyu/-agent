using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace PaperAgent.Desktop.ViewModels;

/// <summary>
/// MVVM 的基础类：属性变化时通知 WPF 刷新界面。
/// </summary>
public abstract class ObservableObject : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected bool SetProperty<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return false;
        }

        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        return true;
    }
}

